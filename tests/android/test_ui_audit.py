import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import Mock
from xml.etree import ElementTree

script = Path(__file__).resolve().parents[2] / "scripts/android_ui_audit.py"
spec = importlib.util.spec_from_file_location("android_ui_audit", script)
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


class AuditDriverTests(unittest.TestCase):
    def test_reads_screenshot_geometry(self):
        header = audit.PNG_SIGNATURE + struct.pack(">I4sII", 13, b"IHDR", 960, 432)
        self.assertEqual(audit.png_size(header), (960, 432))
        with self.assertRaises(ValueError):
            audit.png_size(b"adb transport disconnected")

    def test_selects_visible_unique_android_node(self):
        tree = ElementTree.fromstring(
            '<hierarchy><node text="Parar" bounds="[10,20][58,68]"/></hierarchy>'
        )
        self.assertEqual(audit.node_center(tree, {"text": "Parar"}), (34, 44))
        with self.assertRaisesRegex(ValueError, "matched 0"):
            audit.node_center(tree, {"text": "Play"})
        tree.append(tree[0])
        with self.assertRaisesRegex(ValueError, "matched 2"):
            audit.node_center(tree, {"text": "Parar"})

    def test_coordinate_must_match_reviewed_frame(self):
        device = Mock()
        with tempfile.TemporaryDirectory() as directory:
            log = Path(directory) / "logcat.txt"
            log.touch()
            with self.assertRaisesRegex(ValueError, "matching screenshot size"):
                audit.act(device, {"tap": [20, 30]}, (960, 432), (1280, 576), log)
            device.shell.assert_not_called()
            status = audit.act(device, {"tap": [20, 30]}, (960, 432), (960, 432), log)
            self.assertEqual(status, "captured")
            device.shell.assert_called_once_with("input", "touchscreen", "tap", 20, 30)

    def test_log_assertion_rejects_previous_step_marker(self):
        device = Mock()
        with tempfile.TemporaryDirectory() as directory:
            log = Path(directory) / "logcat.txt"
            marker = "ROOK_FIRST_FRAME_PRESENTED"
            log.write_text(marker + "\n")

            def check_once(predicate, deadline, description):
                if not predicate():
                    raise TimeoutError(description)

            step = {"tap": [20, 30], "expect": {"log": marker}}
            with unittest.mock.patch.object(audit, "wait_for", check_once):
                with self.assertRaises(TimeoutError):
                    audit.act(device, step, (960, 432), (960, 432), log)
                device.shell.side_effect = lambda *_: log.write_text(marker + "\n" + marker + "\n")
                self.assertEqual(audit.act(device, step, (960, 432), (960, 432), log), "passed")

    def test_app_anr_uses_current_session_and_application(self):
        old_anr = b"E ActivityManager: ANR in com.rbxnative.androidport\n"
        session = b"I RbxUiAudit: RBX_UI_AUDIT_42\n"
        unrelated = (
            b"E ActivityManager: ANR in com.android.systemui\n"
            b"E ActivityManager: ANR in com.rbxnative.androidport.fixture\n"
        )
        prefix = old_anr + session + unrelated
        offset = len(old_anr + session)
        app_events = (
            b"E ActivityManager: ANR in com.rbxnative.androidport:rook\n",
            b"I WindowManager: ANR in Window{153eaea u0 com.rbxnative.androidport/dev.gpui.mobile.GpuiActivity}\n",
        )
        with tempfile.TemporaryDirectory() as directory:
            log = Path(directory) / "logcat.txt"
            log.write_bytes(prefix)
            self.assertIsNone(audit.app_anr(log, offset))
            for event in app_events:
                with self.subTest(event=event):
                    log.write_bytes(prefix + event)
                    self.assertEqual(audit.app_anr(log, offset), event.decode().strip())


if __name__ == "__main__":
    unittest.main()
