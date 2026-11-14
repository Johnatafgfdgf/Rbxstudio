local p = game.Players.LocalPlayer
local tool = Instance.new("Tool")
tool.Name = "ClientOnly"
local handle = Instance.new("Part", tool)
handle.Name = "Handle"
handle.Size = Vector3.new(0.5, 1, 0.5)
handle.CanCollide = false
handle.Massless = true
tool.Parent = p.Backpack
local uses = 0
tool.Activated:Connect(function() uses += 1; tool:SetAttribute("Uses", uses) end)
p.Character.Humanoid:EquipTool(tool)
tool.Grip = CFrame.new(0, 0.5, 0)
tool:Activate()
tool:Deactivate()
