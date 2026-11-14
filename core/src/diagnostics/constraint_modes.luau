local function part(name, x, anchored)
    local p = Instance.new("Part", workspace)
    p.Name = name
    p.Size = Vector3.new(2, 2, 2)
    p.Position = Vector3.new(x, 1000, 0)
    p.Anchored = anchored or false
    p.CanCollide = false
    return p, Instance.new("Attachment", p)
end
local function linear(p, a)
    local c = Instance.new("LinearVelocity", p)
    c.Attachment0 = a
    c.RelativeTo = Enum.ActuatorRelativeTo.World
    c.ForceLimitsEnabled = false
    return c
end
local line, a = part("LineMode", 0)
local l = linear(line, a)
l.VelocityConstraintMode = Enum.VelocityConstraintMode.Line
l.LineDirection = Vector3.xAxis
l.LineVelocity = 8
local plane, b = part("PlaneMode", 40)
local p = linear(plane, b)
p.VelocityConstraintMode = Enum.VelocityConstraintMode.Plane
p.PrimaryTangentAxis = Vector3.xAxis
p.SecondaryTangentAxis = Vector3.zAxis
p.PlaneVelocity = Vector2.new(6, 3)
local bounded, c = part("BoundedMode", 80)
local f = linear(bounded, c)
f.VectorVelocity = Vector3.new(100, 0, 0)
f.ForceLimitsEnabled = true
f.ForceLimitMode = Enum.ForceLimitMode.PerAxis
f.MaxAxesForce = Vector3.new(bounded.AssemblyMass * 2, 0, 0)
local first, d = part("Reference0", 120)
local second, e = part("Reference1", 160)
local relative = linear(first, d)
relative.Attachment1 = e
relative.RelativeTo = Enum.ActuatorRelativeTo.Attachment1
relative.VectorVelocity = Vector3.new(10, 0, 0)
relative.ReactionForceEnabled = true
local follower, g = part("Follower", 200)
local target, h = part("FollowTarget", 220, true)
local align = Instance.new("AlignPosition", follower)
align.Attachment0, align.Attachment1 = g, h
align.MaxForce, align.MaxVelocity = 1e8, 100
align.ApplyAtCenterOfMass = true
align.Responsiveness = 30
local changing, j = part("ChangingFrame", 260)
local v = linear(changing, j)
v.RelativeTo = Enum.ActuatorRelativeTo.Attachment0
v.VectorVelocity = Vector3.new(4, 0, 0)
task.delay(0.3, function() j.CFrame = CFrame.Angles(0, math.pi/2, 0) end)
local spin0, sa = part("AngularReaction0", 300)
local spin1, sb = part("AngularReaction1", 340)
local motor = Instance.new("AngularVelocity", spin0)
motor.Attachment0, motor.Attachment1 = sa, sb
motor.RelativeTo = Enum.ActuatorRelativeTo.Attachment1
motor.AngularVelocity = Vector3.new(0, 4, 0)
motor.MaxTorque = 1e8
motor.ReactionTorqueEnabled = true
local axisPart, axisAttachment = part("PrimaryAxisPart", 380)
local axis = Instance.new("AlignOrientation", axisPart)
axis.Attachment0 = axisAttachment
axis.Mode = Enum.OrientationAlignmentMode.OneAttachment
axis.AlignType = Enum.AlignType.PrimaryAxisParallel
axis.CFrame = CFrame.Angles(0, math.pi/2, 0)
axis.MaxTorque = 1e8
axis.Responsiveness = 30
