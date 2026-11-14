local p = game.Players.LocalPlayer or game.Players:GetPlayers()[1]
local character = p.Character
local tool = character:FindFirstChild("DiagnosticTool")
assert(tool, "Tool não entrou no Character")
local hand = character:FindFirstChild("RightHand") or character:FindFirstChild("Right Arm")
local joint = hand:FindFirstChild("RightGrip")
assert(joint and joint.Part0 == hand and joint.Part1 == tool.Handle, "RightGrip ausente ou ligada aos objetos errados")
local count = 0
for _, child in hand:GetChildren() do
    if child.Name == "RightGrip" and child.Part1 == tool.Handle then count += 1 end
end
assert(count == 1, "Juntas RightGrip duplicadas após replicação")
assert((tool.Handle.Position - hand.Position).Magnitude < 4, "Handle não acompanha a mão")
