#!/usr/bin/env python3
"""Compile and execute actual Java XYZ rotation/check helpers, without Android."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = (root / "android/src/com/rbxnative/androidport/StudioAiHub.java").read_text()
start = source.index("    private static boolean closeComponents(")
end = source.index("    /** Compare actual post-write properties", start)
methods = source[start:end].strip()
assert "expectedAnglesMatrix" in methods and "closeComponents" in methods
assert methods.endswith("}")

harness = """
public final class PartMatrixHarness {
    %s

    static void matches(double[] got, double[] want) {
        if (!closeComponents(got, want, 0.000001)) {
            throw new AssertionError("Rotation matrix incorrect");
        }
    }

    static void rotationsAreOrthonormal(double[] r) {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                double dot = 0;
                for (int k = 0; k < 3; k++) dot += r[3 * i + k] * r[3 * j + k];
                double want = i == j ? 1.0 : 0.0;
                if (Math.abs(dot - want) > 0.000001)
                    throw new AssertionError("Not orthonormal");
            }
        }
    }

    public static void main(String[] args) {
        matches(expectedAnglesMatrix(new double[]{0, 0, 0}),
                new double[]{1, 0, 0, 0, 1, 0, 0, 0, 1});
        matches(expectedAnglesMatrix(new double[]{90, 0, 0}),
                new double[]{1, 0, 0, 0, 0, -1, 0, 1, 0});
        matches(expectedAnglesMatrix(new double[]{0, 90, 0}),
                new double[]{0, 0, 1, 0, 1, 0, -1, 0, 0});
        matches(expectedAnglesMatrix(new double[]{0, 0, 90}),
                new double[]{0, -1, 0, 1, 0, 0, 0, 0, 1});
        rotationsAreOrthonormal(expectedAnglesMatrix(new double[]{35, -60, 123}));
        if (closeComponents(new double[]{1}, new double[]{2}, 0.02))
            throw new AssertionError("Accepted unrelated values");
        if (closeComponents(new double[]{Double.NaN}, new double[]{0}, 0.02))
            throw new AssertionError("Accepted NaN");
        System.out.println("Part rotation: 5 matrices, close/far/nonfinite checks passed");
    }
}
""" % methods

with tempfile.TemporaryDirectory(prefix="rbx-part-matrix-") as d:
    path = Path(d) / "PartMatrixHarness.java"
    path.write_text(harness)
    subprocess.run(["javac", "--release", "11", str(path)], check=True, cwd=d)
    subprocess.run(["java", "-cp", d, "PartMatrixHarness"], check=True, cwd=d)
