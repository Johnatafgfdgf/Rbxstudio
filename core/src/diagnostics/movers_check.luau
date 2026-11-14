local p = workspace.MoverVelocity
assert(math.abs(p.AssemblyLinearVelocity.X - 12) < 0.2, "LinearVelocity não atingiu o alvo")
assert(math.abs(p.Position.Y - 1000) < 0.2, "LinearVelocity não sustentou o objeto")
assert(math.abs(workspace.MoverSpin.AssemblyAngularVelocity.Y - 2) < 0.2, "AngularVelocity não girou o objeto")
assert((workspace.MoverPosition.Position - Vector3.new(90, 1005, 0)).Magnitude < 0.5, "AlignPosition não moveu o objeto")
assert(workspace.MoverOrientation.CFrame.LookVector:Dot(Vector3.zAxis) > 0.98, "AlignOrientation não alinhou 180 graus")
