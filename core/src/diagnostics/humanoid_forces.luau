local p = game.Players.LocalPlayer
local character = p.Character
local humanoid = character.Humanoid
character:PivotTo(CFrame.new(0, 12, 0))
humanoid:ChangeState(Enum.HumanoidStateType.Physics)
humanoid.AutoRotate = false
local root = character.HumanoidRootPart
local a = Instance.new("Attachment", root)
a.Name = "DiagnosticForceAttachment"
local v = Instance.new("LinearVelocity", root)
v.Name = "DiagnosticLinearVelocity"
v.Attachment0 = a
v.RelativeTo = Enum.ActuatorRelativeTo.World
v.VectorVelocity = Vector3.new(0, 14, -6)
v.ForceLimitsEnabled = false
root:SetAttribute("StartY", root.Position.Y)
