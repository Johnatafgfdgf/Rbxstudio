local line = workspace.LineMode.AssemblyLinearVelocity
assert(math.abs(line.X-8) < 0.1 and line.Y < -100, "Modo Line restringiu um eixo livre ou ignorou o alvo")
local plane = workspace.PlaneMode.AssemblyLinearVelocity
assert(math.abs(plane.X-6) < 0.1 and math.abs(plane.Z-3) < 0.1 and plane.Y < -100, "Modo Plane incorreto")
local bounded = workspace.BoundedMode.AssemblyLinearVelocity
assert(bounded.X > 1.5 and bounded.X < 3.5 and bounded.Y < -100, "MaxAxesForce ignorado")
local a, b = workspace.Reference0.AssemblyLinearVelocity, workspace.Reference1.AssemblyLinearVelocity
assert(math.abs((a-b).X - 10) < 0.1, "Velocidade relativa a Attachment1 incorreta")
assert(math.abs(a.X+b.X) < 0.1, "Reação não conservou o momento")
assert((workspace.Follower.Position-workspace.FollowTarget.Position).Magnitude < 0.5, "Modo TwoAttachment não acompanhou o alvo")
local changed = workspace.ChangingFrame.AssemblyLinearVelocity
assert(math.abs(changed.X) < 0.1 and math.abs(changed.Z+4) < 0.1, "Alteração no Attachment não atualizou a simulação")
local spin0, spin1 = workspace.AngularReaction0.AssemblyAngularVelocity, workspace.AngularReaction1.AssemblyAngularVelocity
assert(math.abs((spin0-spin1).Y - 4) < 0.1 and math.abs(spin0.Y+spin1.Y) < 0.1, "Reação de AngularVelocity incorreta")
assert(workspace.PrimaryAxisPart.CFrame.RightVector:Dot(-Vector3.zAxis) > 0.98, "AlignType.PrimaryAxisParallel não alinhou o eixo")
