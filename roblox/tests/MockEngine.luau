--!nonstrict
-- Small deterministic Roblox API mock. This is not an engine/physics emulator.
local Mock = {}
local vectorMt = {}
vectorMt.__index = function(v, key)
    if key == "Magnitude" then return math.sqrt(v.X*v.X+v.Y*v.Y+v.Z*v.Z) end
    if key == "Unit" then local m=math.sqrt(v.X*v.X+v.Y*v.Y+v.Z*v.Z); return Mock.Vector3.new(v.X/m,v.Y/m,v.Z/m) end
end
vectorMt.__add = function(a,b) return Mock.Vector3.new(a.X+b.X,a.Y+b.Y,a.Z+b.Z) end
vectorMt.__sub = function(a,b) return Mock.Vector3.new(a.X-b.X,a.Y-b.Y,a.Z-b.Z) end
vectorMt.__mul = function(a,b)
    if type(a)=="number" then a,b=b,a end
    return Mock.Vector3.new(a.X*b,a.Y*b,a.Z*b)
end
Mock.Vector3 = {new=function(x,y,z) return setmetatable({X=x,Y=y,Z=z},vectorMt) end}
Mock.Vector3.zero=Mock.Vector3.new(0,0,0); Mock.Vector3.yAxis=Mock.Vector3.new(0,1,0)
local frameMt = {}
frameMt.__mul = function(a,b) return Mock.CFrame.new(a.Position+b.Position) end
Mock.CFrame = {
    new=function(x,y,z)
        local position=if type(x)=="table" then x else Mock.Vector3.new(x or 0,y or 0,z or 0)
        return setmetatable({Position=position,LookVector=Mock.Vector3.new(0,0,-1),RightVector=Mock.Vector3.new(1,0,0)},frameMt)
    end,
    Angles=function() return Mock.CFrame.new() end,
}
Mock.Enum = {
    CameraType={Custom="Custom",Scriptable="Scriptable"},
    ActuatorRelativeTo={World="World"},VelocityConstraintMode={Vector="Vector"},
    Material={Air="Air",Concrete="Concrete"},HumanoidStateType={Jumping="Jumping"},
    ContextActionResult={Sink="Sink"},ContextActionPriority={High={Value=3000}},
    MouseBehavior={Default="Default",LockCurrentPosition="LockCurrentPosition"},
    UserInputType={MouseButton2="MouseButton2"},
    KeyCode={W="W",A="A",S="S",D="D",Space="Space",LeftControl="LeftControl",RightControl="RightControl"},
}
Mock.Color3={fromRGB=function(r,g,b) return {R=r,G=g,B=b} end}
Mock.ColorSequence={new=function(a,b) return {a,b} end}
function Mock.signal()
    local signal={connections={}}
    function signal:Connect(fn)
        local connection={Connected=true,fn=fn}
        function connection:Disconnect() self.Connected=false end
        table.insert(self.connections,connection)
        return connection
    end
    function signal:Fire(...)
        for _,c in table.clone(self.connections) do if c.Connected then c.fn(...) end end
    end
    return signal
end
local instances={}
Mock.Instance={new=function(className)
    local object={ClassName=className,Name=className,Parent=nil,Destroyed=false}
    function object:Destroy() self.Destroyed=true; self.Parent=nil end
    function object:IsA(name) return self.ClassName==name or (name=="BasePart" and self.ClassName=="Part") end
    table.insert(instances,object)
    return object
end}
function Mock.makeCharacter()
    local root=Mock.Instance.new("Part")
    root.Name="HumanoidRootPart"; root.CFrame=Mock.CFrame.new(0,8,0); root.Anchored=false
    root.CanCollide=true; root.AssemblyMass=10
    root.AssemblyLinearVelocity=Mock.Vector3.zero; root.AssemblyAngularVelocity=Mock.Vector3.zero
    root.impulses={}
    function root:ApplyImpulse(vector) table.insert(self.impulses,vector) end
    local head=Mock.Instance.new("Part"); head.CanCollide=true
    local humanoid=Mock.Instance.new("Humanoid")
    humanoid.Health=100; humanoid.WalkSpeed=16; humanoid.JumpPower=50; humanoid.JumpHeight=7.2
    humanoid.AutoRotate=true; humanoid.FloorMaterial=Mock.Enum.Material.Concrete; humanoid.Died=Mock.signal()
    function humanoid:ChangeState(state) self.lastState=state end
    local character={root=root,humanoid=humanoid,head=head}
    root.Parent=character; humanoid.Parent=character; head.Parent=character
    function character:FindFirstChild(name) return if name=="HumanoidRootPart" then root else nil end
    function character:FindFirstChildOfClass(name) return if name=="Humanoid" then humanoid else nil end
    function character:GetDescendants() return {root,head,humanoid} end
    function character:GetPivot() return root.CFrame end
    function character:PivotTo(frame) root.CFrame=frame end
    return character
end
function Mock.make()
    local character=Mock.makeCharacter()
    local player={UserId=42,Name="TestPlayer",Character=character,CharacterAdded=Mock.signal(),CharacterRemoving=Mock.signal()}
    local target={UserId=43,Name="Friend",Character=Mock.makeCharacter()}
    local input={keys={},mouse=false,MouseBehavior=Mock.Enum.MouseBehavior.Default,JumpRequest=Mock.signal()}
    function input:GetFocusedTextBox() return self.textBox end
    function input:IsKeyDown(key) return self.keys[key]==true end
    function input:IsMouseButtonPressed() return self.mouse end
    function input:GetMouseDelta() return {X=4,Y=2} end
    local camera={CFrame=Mock.CFrame.new(0,10,20),Focus=Mock.CFrame.new(0,8,0),FieldOfView=70,CameraType="Custom",CameraSubject=character.humanoid}
    local services={
        Workspace={Gravity=196.2,CurrentCamera=camera},
        Lighting={Brightness=1,Ambient=Mock.Color3.fromRGB(70,70,70),OutdoorAmbient=Mock.Color3.fromRGB(100,100,100),ClockTime=12},
        UserInputService=input,RunService={RenderStepped=Mock.signal()},
        ContextActionService={bindings={}},Players={LocalPlayer=player},
    }
    function services.ContextActionService:BindActionAtPriority(name,...) self.bindings[name]={...} end
    function services.ContextActionService:UnbindAction(name) self.bindings[name]=nil end
    function services.Players:GetPlayerByUserId(id) return if id==42 then player elseif id==43 then target else nil end
    function services.Players:GetPlayers() return {player,target} end
    local now=0
    local transport={packets={}}
    function transport:publish(snapshot,result) table.insert(self.packets,{snapshot=snapshot,result=result}) end
    function transport:subscribe(handler) self.handler=handler; return function() self.unsubscribed=true; self.handler=nil end end
    return {engine=Mock,services=services,player=player,target=target,transport=transport,
        now=function() return now end,advance=function(delta) now+=delta or 1 end,instances=instances}
end
return Mock
