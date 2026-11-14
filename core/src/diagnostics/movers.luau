local function part(name, x)
    local p = Instance.new("Part", workspace)
    p.Name = name
    p.Size = Vector3.new(2, 2, 2)
    p.Position = Vector3.new(x, 1000, 0)
    p.CanCollide = false
    return p, Instance.new("Attachment", p)
end
local p, a = part("MoverVelocity", 0)
local v = Instance.new("LinearVelocity", p)
v.Attachment0 = a
v.RelativeTo = Enum.ActuatorRelativeTo.World
v.VectorVelocity = Vector3.new(12, 0, 0)
v.ForceLimitsEnabled = false
local spin, b = part("MoverSpin", 40)
local angular = Instance.new("AngularVelocity", spin)
angular.Attachment0 = b
angular.RelativeTo = Enum.ActuatorRelativeTo.World
angular.AngularVelocity = Vector3.new(0, 2, 0)
angular.MaxTorque = 1e6
local pos, c = part("MoverPosition", 80)
local align = Instance.new("AlignPosition", pos)
align.Attachment0 = c
align.Mode = Enum.PositionAlignmentMode.OneAttachment
align.Position = Vector3.new(90, 1005, 0)
align.ApplyAtCenterOfMass = true
align.MaxForce = 1e8
align.Responsiveness = 20
local orientation, d = part("MoverOrientation", 120)
local rotation = Instance.new("AlignOrientation", orientation)
rotation.Attachment0 = d
rotation.Mode = Enum.OrientationAlignmentMode.OneAttachment
rotation.CFrame = CFrame.Angles(0, math.pi, 0)
rotation.MaxTorque = 1e8
rotation.Responsiveness = 20
