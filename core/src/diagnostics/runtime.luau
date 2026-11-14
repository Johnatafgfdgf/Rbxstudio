local module = Instance.new("ModuleScript", game.ReplicatedStorage)
module.Source = "return {value=42}"
assert(require(module).value == 42)
local p = Instance.new("Part", workspace)
p.Name = "RuntimePart"
p.Anchored = true
p.Position = Vector3.new(0, 20, 0)
p.Color = Color3.new(1, 0, 0)
local copy = p:Clone()
copy.Parent = workspace
copy:Destroy()
assert(copy.Parent == nil)
local event = Instance.new("BindableEvent")
local count = 0
event.Event:Connect(function(value) count += value end)
task.spawn(function() event:Fire(2) end)
task.delay(0.1, function() event:Fire(3) end)
local tween = game:GetService("TweenService"):Create(p, TweenInfo.new(0.2), {Transparency=0.5})
tween:Play()
task.spawn(function()
    tween.Completed:Wait()
    task.wait(0.1)
    assert(count == 5, "task ou BindableEvent falhou")
    assert(math.abs(p.Transparency - 0.5) < 0.01, "Tween não alterou a propriedade")
    workspace:SetAttribute("RuntimeDone", true)
end)
