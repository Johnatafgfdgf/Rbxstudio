local floor = Instance.new("Part", workspace)
floor.Anchored = true
floor.Size = Vector3.new(200, 1, 200)
floor.Position = Vector3.new(0, -0.5, 0)
local prototype = Instance.new("Tool", game:GetService("ServerStorage"))
prototype.Name = "DiagnosticTool"
local handle = Instance.new("Part", prototype)
handle.Name = "Handle"
handle.Size = Vector3.new(0.5, 2, 0.5)
handle.CanCollide = false
handle.Massless = true
local behavior = Instance.new("Script", prototype)
behavior.Source = [[
    local tool = script.Parent
    tool.Activated:Connect(function() workspace:SetAttribute("ToolUses", (workspace:GetAttribute("ToolUses") or 0)+1) end)
    tool.Deactivated:Connect(function() workspace:SetAttribute("ToolReleases", (workspace:GetAttribute("ToolReleases") or 0)+1) end)
    tool.Unequipped:Connect(function() workspace:SetAttribute("ToolUnequips", (workspace:GetAttribute("ToolUnequips") or 0)+1) end)
]]
local grant = Instance.new("RemoteEvent", game:GetService("ReplicatedStorage"))
grant.Name = "GrantDiagnosticTool"
grant.OnServerEvent:Connect(function(player)
    if not player.Backpack:FindFirstChild(prototype.Name) and not player.Character:FindFirstChild(prototype.Name) then
        prototype:Clone().Parent = player.Backpack
    end
end)
