local p = game.Players.LocalPlayer
p.Character:PivotTo(CFrame.new(20, 6, 0))
local camera = workspace.CurrentCamera
camera.CameraType = Enum.CameraType.Scriptable
camera.CFrame = CFrame.lookAt(Vector3.new(0, 6, 0), Vector3.new(0, 6, -8))
local tool = Instance.new("Tool", p.Backpack)
tool.RequiresHandle = false
tool.Equipped:Connect(function(mouse)
    tool.Activated:Connect(function()
        assert(mouse.Target == workspace.AimTarget, "Mouse.Target da Tool ainda aponta para o cursor anterior")
        assert((mouse.Hit.Position - Vector3.new(0, 6, -7)).Magnitude < 0.2, "Mouse.Hit não corresponde ao toque atual")
        workspace:SetAttribute("AimUses", (workspace:GetAttribute("AimUses") or 0)+1)
    end)
end)
p.Character.Humanoid:EquipTool(tool)
