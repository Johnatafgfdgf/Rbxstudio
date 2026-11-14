local player = game.Players.LocalPlayer
local gui = Instance.new("ScreenGui", player.PlayerGui)
gui.Name = "DiagnosticGrant"
gui.IgnoreGuiInset = true
local button = Instance.new("TextButton", gui)
button.Text = "Receber item"
button.Position = UDim2.fromOffset(24, 24)
button.Size = UDim2.fromOffset(180, 48)
button.Activated:Connect(function()
    game.ReplicatedStorage.GrantDiagnosticTool:FireServer()
end)
