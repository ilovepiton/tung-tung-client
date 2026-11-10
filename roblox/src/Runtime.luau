--!nonstrict
-- Standard Roblox client APIs only. This is not an injector or a standalone executor.
-- A creator-controlled LocalScript or an already-compatible approved host must load it.
local Catalog = if script then require(script.Parent.Catalog) else require("./Catalog")
local Policy = if script then require(script.Parent.Policy) else require("./Policy")
local Lease = if script then require(script.Parent.PropertyLease) else require("./PropertyLease")

local Runtime = {}
Runtime.__index = Runtime
local shaderPresets = {
    warm={brightness=0.02, contrast=0.08, saturation=0.12, tint={255,236,217}, bloom=0.3},
    cinematic={brightness=-0.01, contrast=0.15, saturation=-0.05, tint={238,242,255}, bloom=0.18},
    vibrant={brightness=0.02, contrast=0.09, saturation=0.3, tint={255,255,255}, bloom=0.4},
    clean={brightness=0, contrast=0.03, saturation=0.02, tint={255,255,255}, bloom=0.1},
}

local function copy(source)
    local target = {}
    for key, value in source do target[key] = value end
    return target
end

function Runtime.new(options)
    options = options or {}
    local services = options.services
    if not services then
        services = {}
        for _, name in {"Players", "Workspace", "Lighting", "RunService", "UserInputService", "ContextActionService"} do
            services[name] = game:GetService(name)
        end
    end
    local self = setmetatable({
        services=services,
        player=options.player or services.Players.LocalPlayer,
        engine=options.engine or {Instance=Instance, Vector3=Vector3, CFrame=CFrame, Color3=Color3, Enum=Enum},
        transport=options.transport,
        serverAdapter=options.serverAdapter,
        now=options.now or os.clock,
        active={}, values={}, status={}, targets={}, objects={}, collisions={}, connections={},
        characterLease=Lease.new(), cameraLease=Lease.new(), lightLease=Lease.new(), inputLease=Lease.new(),
        last={}, bucket={tokens=20, updated=0}, destroyed=false, started=false,
        airborneJumps=0, freecamPosition=nil, yaw=0, pitch=0, publishedAt=0,
        shaderPreset="warm", revision=0, currentCamera=services.Workspace.CurrentCamera,
    }, Runtime)
    assert(self.player, "A local Roblox player is required.")
    self.bucket.updated = self.now()
    for _, entry in Catalog.entries do
        self.active[entry.id] = false
        self.values[entry.id] = entry.default
        self.status[entry.id] = if entry.scope == "server-required" then "Requires an authorized game adapter" else "Off"
    end
    return self
end

function Runtime:_new(className, name, parent)
    local instance = self.engine.Instance.new(className)
    instance.Name = "TungTung_" .. name
    instance.Parent = parent
    return instance
end

function Runtime:_character()
    local character = self.player.Character
    if not character then return nil end
    local humanoid = character:FindFirstChildOfClass("Humanoid")
    local root = character:FindFirstChild("HumanoidRootPart")
    if not humanoid or not root or humanoid.Health <= 0 then return nil end
    return character, humanoid, root
end

function Runtime:_remove(id)
    local list = self.objects[id]
    if list then
        for _, object in list do pcall(function() object:Destroy() end) end
        self.objects[id] = nil
    end
end

function Runtime:_attach(id, root)
    self:_remove(id)
    local attachment = self:_new("Attachment", id .. "Attachment", root)
    self.objects[id] = {attachment}
    return attachment
end

function Runtime:_publish(result)
    if self.transport and type(self.transport.publish) == "function" then
        local ok, message = pcall(self.transport.publish, self.transport, self:snapshot(), result)
        if not ok then self.transportError = tostring(message):sub(1, 200) end
    end
end

function Runtime:snapshot()
    self.revision += 1
    local character, humanoid, root = self:_character()
    local modules = {}
    for _, entry in Catalog.entries do
        local serverAvailable = self.serverAdapter ~= nil and type(self.serverAdapter.request)=="function"
        if entry.id=="friend_fling" then serverAvailable=serverAvailable and type(self.serverAdapter.getConsent)=="function" end
        local available = entry.scope ~= "server-required" or serverAvailable
        table.insert(modules, {
            id=entry.id, enabled=self.active[entry.id] == true,
            value=self.values[entry.id], status=self.status[entry.id],
            scope=if entry.scope=="server-required" then (if serverAvailable then "server" else "unavailable") else "local",
            effectScope=entry.scope, serverConfirmed=false, available=available,
        })
    end
    local players = {}
    for _, player in self.services.Players:GetPlayers() do
        -- Native protocol accepts real positive UserIds; Studio's synthetic negatives are omitted.
        if player.UserId > 0 and #players < 200 then
            table.insert(players, {userId=player.UserId, name=player.Name, displayName=player.DisplayName or player.Name,
                isLocal=player==self.player, flingAllowed=self:_hasConsent(player.UserId)})
        end
    end
    return {
        protocol=Catalog.protocol, runtimeVersion=Catalog.version, revision=self.revision,
        connected=self.started and not self.destroyed,
        hostRequired=true, serverConfirmed=false,
        player={userId=self.player.UserId, name=self.player.Name},
        characterReady=character ~= nil,
        observed=if root then {walkSpeed=humanoid.WalkSpeed, anchored=root.Anchored, health=humanoid.Health} else {},
        modules=modules, players=players, transportError=self.transportError,
        settings={flySpeed=self.values.fly, walkSpeed=self.values.speed,
            freecamSpeed=self.values.freecam, fov=self.values.fov, shaderPreset=self.shaderPreset},
    }
end

function Runtime:_hasConsent(userId)
    local adapter=self.serverAdapter
    if not adapter or type(adapter.getConsent)~="function" then return false end
    -- Read-only, synchronous server-derived consent cache supplied by a trusted game adapter.
    -- Native UI, local attributes, display names, and user command fields are not consent proof.
    local ok, receipt=pcall(adapter.getConsent,adapter,userId)
    return ok and type(receipt)=="table" and receipt.serverConfirmed==true
        and receipt.targetUserId==userId and receipt.consented==true
end

function Runtime:_ack(command, ok, message, appliedLocally, serverConfirmed)
    self.status[command.id] = message
    local result = {requestId=command.requestId, id=command.id, ok=ok,
        status=message, enabled=self.active[command.id] == true,
        appliedLocally=appliedLocally == true, serverConfirmed=serverConfirmed == true}
    self:_publish(result)
    return result
end

function Runtime:_key(name)
    if self.services.UserInputService:GetFocusedTextBox() then return false end
    return self.services.UserInputService:IsKeyDown(self.engine.Enum.KeyCode[name])
end

function Runtime:_direction(flat)
    local E = self.engine
    local camera = self.services.Workspace.CurrentCamera
    if not camera then return E.Vector3.zero end
    local forward, right = camera.CFrame.LookVector, camera.CFrame.RightVector
    if flat then
        forward = E.Vector3.new(forward.X, 0, forward.Z)
        right = E.Vector3.new(right.X, 0, right.Z)
        if forward.Magnitude > 0.01 then forward = forward.Unit end
        if right.Magnitude > 0.01 then right = right.Unit end
    end
    local vector = E.Vector3.zero
    if self:_key("W") then vector += forward end
    if self:_key("S") then vector -= forward end
    if self:_key("D") then vector += right end
    if self:_key("A") then vector -= right end
    if self:_key("Space") then vector += E.Vector3.yAxis end
    if self:_key("LeftControl") or self:_key("RightControl") then vector -= E.Vector3.yAxis end
    return if vector.Magnitude > 1 then vector.Unit else vector
end

function Runtime:_restoreCamera()
    self.services.ContextActionService:UnbindAction("TungTungFreecam")
    self.inputLease:restoreAll()
    self.cameraLease:restoreAll()
    self.freecamPosition = nil
end

function Runtime:_enableCamera(id, enabled, targetUserId)
    local E = self.engine
    local camera = self.services.Workspace.CurrentCamera
    if not camera then return false, "Camera is not ready." end
    if id == "fov" then
        if enabled then self.cameraLease:write(camera, "FieldOfView", self.values.fov)
        else self.cameraLease:restore(camera, "FieldOfView") end
        return true
    end
    if not enabled then
        if not self.active[id] then return true end
        -- FOV has an independent lease and is reapplied after returning the camera.
        self:_restoreCamera()
        if self.active.fov then self.cameraLease:write(camera, "FieldOfView", self.values.fov) end
        return true
    end
    self:_restoreCamera()
    local other = if id == "freecam" then "spectate" else "freecam"
    self.active[other] = false
    self.status[other] = "Off"
    if id == "spectate" then
        local target = self.services.Players:GetPlayerByUserId(targetUserId)
        local humanoid = target and target.Character and target.Character:FindFirstChildOfClass("Humanoid")
        if not humanoid or humanoid.Health <= 0 then return false, "Selected player has no living character." end
        self.targets.spectate = targetUserId
        self.cameraLease:write(camera, "CameraType", E.Enum.CameraType.Custom)
        self.cameraLease:write(camera, "CameraSubject", humanoid)
    else
        self.freecamPosition = camera.CFrame.Position
        local look = camera.CFrame.LookVector
        self.yaw = math.atan2(-look.X, -look.Z)
        self.pitch = math.asin(math.clamp(look.Y, -1, 1))
        self.cameraLease:write(camera, "CameraType", E.Enum.CameraType.Scriptable)
        self.cameraLease:write(camera, "CFrame", camera.CFrame)
        self.cameraLease:write(camera, "Focus", camera.Focus)
        self.services.ContextActionService:BindActionAtPriority("TungTungFreecam", function()
            return E.Enum.ContextActionResult.Sink
        end, false, E.Enum.ContextActionPriority.High.Value + 1,
            E.Enum.KeyCode.W, E.Enum.KeyCode.A, E.Enum.KeyCode.S, E.Enum.KeyCode.D,
            E.Enum.KeyCode.Space, E.Enum.KeyCode.LeftControl, E.Enum.KeyCode.RightControl)
    end
    if self.active.fov then self.cameraLease:write(camera, "FieldOfView", self.values.fov) end
    return true
end

function Runtime:_visual(id, enabled)
    local E, lighting = self.engine, self.services.Lighting
    self:_remove(id)
    if id == "fullbright" then
        if enabled then
            self.lightLease:write(lighting, "Brightness", 2.5)
            self.lightLease:write(lighting, "Ambient", E.Color3.fromRGB(190, 190, 190))
            self.lightLease:write(lighting, "OutdoorAmbient", E.Color3.fromRGB(190, 190, 190))
        else
            for _, key in {"Brightness", "Ambient", "OutdoorAmbient"} do self.lightLease:restore(lighting, key) end
        end
    elseif id == "sunset" then
        if enabled then self.lightLease:write(lighting, "ClockTime", 17.6)
        else self.lightLease:restore(lighting, "ClockTime") end
    elseif enabled then
        local className = if id == "bloom" then "BloomEffect" elseif id == "depth_of_field" then "DepthOfFieldEffect" else "ColorCorrectionEffect"
        local effect = self:_new(className, id, lighting)
        self.objects[id] = {effect}
        effect.Enabled = true
        if id == "bloom" then
            effect.Intensity, effect.Size, effect.Threshold = shaderPresets[self.shaderPreset].bloom, 24, 1.1
        elseif id == "depth_of_field" then
            effect.FarIntensity, effect.NearIntensity = 0.2, 0.1
            effect.FocusDistance, effect.InFocusRadius = 40, 30
        else
            local preset = shaderPresets[self.shaderPreset]
            effect.Brightness, effect.Contrast, effect.Saturation = preset.brightness, preset.contrast, preset.saturation
            effect.TintColor = E.Color3.fromRGB(table.unpack(preset.tint))
        end
    end
    return true
end

function Runtime:_characterToggle(id, enabled)
    local E = self.engine
    local character, humanoid, root = self:_character()
    if not character then return false, "Your character is not ready." end
    self:_remove(id)
    if id == "speed" then
        if enabled then self.characterLease:write(humanoid, "WalkSpeed", self.values.speed)
        else self.characterLease:restore(humanoid, "WalkSpeed") end
    elseif id == "super_jump" then
        if enabled then
            local factor = self.values.super_jump
            -- Factor is height, so JumpPower scales with the square root.
            self.characterLease:write(humanoid, "JumpPower", self.baseJumpPower * math.sqrt(factor))
            self.characterLease:write(humanoid, "JumpHeight", self.baseJumpHeight * factor)
        else
            self.characterLease:restore(humanoid, "JumpPower")
            self.characterLease:restore(humanoid, "JumpHeight")
        end
    elseif id == "freeze" then
        if enabled then self.characterLease:write(root, "Anchored", true)
        else self.characterLease:restore(root, "Anchored") end
    elseif id == "noclip" then
        if enabled then
            for _, part in character:GetDescendants() do
                if part:IsA("BasePart") then self.collisions[part] = true; self.characterLease:write(part, "CanCollide", false) end
            end
        else
            for part in self.collisions do self.characterLease:restore(part, "CanCollide") end
            self.collisions = {}
        end
    elseif id == "fly" and enabled then
        local attachment = self:_attach(id, root)
        local velocity = self:_new("LinearVelocity", "FlyVelocity", root)
        velocity.Attachment0 = attachment
        velocity.RelativeTo = E.Enum.ActuatorRelativeTo.World
        velocity.VelocityConstraintMode = E.Enum.VelocityConstraintMode.Vector
        velocity.ForceLimitsEnabled = true
        velocity.MaxForce = math.clamp(root.AssemblyMass * self.services.Workspace.Gravity * 8, 1000, 4000000)
        velocity.VectorVelocity = E.Vector3.zero
        table.insert(self.objects[id], velocity)
        self.flyVelocity = velocity
    elseif id == "low_gravity" and enabled then
        local attachment = self:_attach(id, root)
        local force = self:_new("VectorForce", "LowGravity", root)
        force.Attachment0 = attachment
        force.ApplyAtCenterOfMass = true
        force.RelativeTo = E.Enum.ActuatorRelativeTo.World
        force.Force = E.Vector3.new(0, root.AssemblyMass * self.services.Workspace.Gravity * (1 - self.values.low_gravity), 0)
        table.insert(self.objects[id], force)
        self.gravityForce = force
    elseif id == "spin" then
        if enabled then
            self.characterLease:write(humanoid, "AutoRotate", false)
            local attachment = self:_attach(id, root)
            local velocity = self:_new("AngularVelocity", "SpinVelocity", root)
            velocity.Attachment0 = attachment
            velocity.RelativeTo = E.Enum.ActuatorRelativeTo.World
            velocity.MaxTorque = math.clamp(root.AssemblyMass * 10000, 1000, 4000000)
            velocity.AngularVelocity = E.Vector3.new(0, self.values.spin, 0)
            table.insert(self.objects[id], velocity)
        else self.characterLease:restore(humanoid, "AutoRotate") end
    elseif id == "trails" and enabled then
        local a = self:_new("Attachment", "TrailTop", root)
        local b = self:_new("Attachment", "TrailBottom", root)
        a.Position, b.Position = E.Vector3.new(-0.8, 0.8, 0.4), E.Vector3.new(0.8, -0.8, 0.4)
        local trail = self:_new("Trail", "AvatarTrail", root)
        trail.Attachment0, trail.Attachment1 = a, b
        trail.Lifetime, trail.MinLength, trail.FaceCamera = 0.35, 0.1, true
        -- ColorSequence is an engine datatype; provide it through the injected engine in tests.
        local sequence = self.engine.ColorSequence or ColorSequence
        trail.Color = sequence.new(E.Color3.fromRGB(255, 155, 74), E.Color3.fromRGB(176, 93, 44))
        self.objects[id] = {a, b, trail}
    end
    return true
end

function Runtime:_action(command)
    local id, now = command.id, self.now()
    local character, humanoid, root = self:_character()
    if not character then return false, "Your character is not ready." end
    if id == "respawn" or id == "friend_fling" then
        local adapter = self.serverAdapter
        if not adapter or type(adapter.request) ~= "function" then
            return false, "Unavailable: this game has no authorized server adapter."
        end
        if id=="friend_fling" and not self:_hasConsent(command.targetUserId) then
            return false, "The trusted game adapter has not confirmed this target's current opt-in."
        end
        -- Only the game server can establish another player's actual opt-in.
        -- A desktop flag or client-reported consent is never accepted as proof.
        local ok, result = pcall(adapter.request, adapter, command)
        if not ok or type(result) ~= "table" or result.serverConfirmed ~= true then
            return false, "No server-confirmed action receipt."
        end
        if id == "friend_fling" and result.targetConsented ~= true then
            return false, "The server did not confirm the target's explicit consent."
        end
        return true, "Authorized game server confirmed the action.", true
    end
    if root.Anchored then return false, "Turn off Freeze Self before moving." end
    if humanoid.SeatPart then return false, "Leave your seat before using a movement action." end
    local E = self.engine
    local forward = root.CFrame.LookVector
    forward = E.Vector3.new(forward.X, 0, forward.Z)
    if forward.Magnitude < 0.01 then forward = E.Vector3.new(0, 0, -1) else forward = forward.Unit end
    local cooldown = if id == "self_fling" then 3 elseif id == "return_spawn" then 2 elseif id == "slide" then 1.5 else 2
    if id == "slide" and humanoid.FloorMaterial == E.Enum.Material.Air then return false, "Slide starts on the ground." end
    if not Policy.cooldown(self.last, id, now, cooldown) then return false, "Action is cooling down." end
    if id == "return_spawn" then
        if not self.spawnCFrame then return false, "No spawn position is available." end
        root.AssemblyLinearVelocity, root.AssemblyAngularVelocity = E.Vector3.zero, E.Vector3.zero
        character:PivotTo(self.spawnCFrame)
    else
        local velocity = if id == "self_fling" then forward * 45 + E.Vector3.new(0, 65, 0)
            elseif id == "slide" then forward * 35 else forward * 45 + E.Vector3.new(0, 5, 0)
        root:ApplyImpulse(velocity * math.clamp(root.AssemblyMass, 0, 1000))
    end
    return true, "Applied to your local character; the game server can correct movement."
end

function Runtime:configure(settings)
    if self.destroyed then return {ok=false, status="Runtime has stopped.", serverConfirmed=false} end
    if type(settings) ~= "table" then return {ok=false, status="Expected settings."} end
    local mapping = {flySpeed="fly", walkSpeed="speed", freecamSpeed="freecam", fov="fov"}
    local staged = {}
    for key, value in settings do
        local id = mapping[key]
        if id then
            if not Policy.finite(value) then return {ok=false, status="Settings must contain finite numbers."} end
            local entry = Catalog.byId[id]
            staged[id] = math.clamp(value, entry.min, entry.max)
        elseif key == "shaderPreset" then
            if type(value) ~= "string" or not shaderPresets[value] then return {ok=false, status="Unknown shader preset."} end
        else return {ok=false, status="Unknown setting: " .. tostring(key):sub(1, 48)} end
    end
    for id, value in staged do self.values[id] = value end
    if settings.shaderPreset then self.shaderPreset = settings.shaderPreset end
    for id in staged do
        if self.active[id] then
            if id == "fov" then self:_enableCamera(id, true) else self:_characterToggle(id, true) end
        end
    end
    if settings.shaderPreset then
        for _, id in {"bloom", "color_grade"} do if self.active[id] then self:_visual(id, true) end end
    end
    local result={ok=true, status="Local settings updated", serverConfirmed=false}
    self:_publish(result)
    return result
end

function Runtime:dispatch(raw)
    if self.destroyed then return {ok=false, status="Runtime has stopped.", serverConfirmed=false} end
    if type(raw)=="table" and (raw.type=="settings" or raw.command=="settings" or raw.command=="setSettings") then return self:configure(raw.settings) end
    if type(raw)=="table" and raw.command=="playerAction" then
        raw=copy(raw); raw.id=raw.action; raw.enabled=true
    end
    -- Desktop uses moduleId; native and in-game hosts may use the canonical id.
    if type(raw) == "table" and raw.id == nil and raw.moduleId ~= nil then raw=copy(raw); raw.id=raw.moduleId end
    local command, message = Policy.validate(raw, Catalog)
    if not command then return {ok=false, status=message, serverConfirmed=false} end
    if not Policy.spend(self.bucket, self.now()) then return self:_ack(command, false, "Too many commands. Try again shortly.", false) end
    local entry = Catalog.byId[command.id]
    if command.value ~= nil then self.values[command.id] = command.value end
    if entry.kind == "action" then
        local called, ok, status, serverConfirmed = pcall(self._action, self, command)
        if not called then return self:_ack(command, false, "Action could not be applied: " .. tostring(ok):sub(1, 160), false) end
        return self:_ack(command, ok, status, ok and not serverConfirmed, serverConfirmed)
    end
    local ok, status
    local called, failure = pcall(function()
        if entry.scope == "client" then
            if entry.category == "Camera" then ok, status = self:_enableCamera(command.id, command.enabled, command.targetUserId)
            else ok, status = self:_visual(command.id, command.enabled) end
        else ok, status = self:_characterToggle(command.id, command.enabled) end
    end)
    if not called then
        self:_remove(command.id)
        self.active[command.id] = false
        return self:_ack(command, false, "Could not apply: " .. tostring(failure):sub(1, 160), false)
    end
    if not ok then self.active[command.id]=false; return self:_ack(command, false, status or "Unavailable.", false) end
    self.active[command.id] = command.enabled
    local label = if not command.enabled then "Off" elseif entry.scope == "client" then "Applied locally" else "Applied locally · server may correct"
    return self:_ack(command, true, label, true)
end

function Runtime:_resetCharacter()
    if self.characterDied then self.characterDied:Disconnect(); self.characterDied=nil end
    for _, entry in Catalog.entries do
        if entry.scope ~= "client" then self.active[entry.id] = false; self.status[entry.id] = "Off" end
    end
    self.characterLease:restoreAll()
    for _, id in {"fly", "low_gravity", "spin", "trails"} do self:_remove(id) end
    self.collisions = {}
    self.flyVelocity, self.gravityForce = nil, nil
    self.airborneJumps = 0
    self.currentCharacter = nil
    self:_publish()
end

function Runtime:_adoptCharacter()
    self:_resetCharacter()
    local character, humanoid = self:_character()
    if character then
        self.currentCharacter = character
        self.spawnCFrame = character:GetPivot()
        self.baseJumpPower, self.baseJumpHeight = humanoid.JumpPower, humanoid.JumpHeight
        self.characterDied = humanoid.Died:Connect(function() self:_resetCharacter() end)
    end
end

function Runtime:_jump()
    if self.destroyed or self.services.UserInputService:GetFocusedTextBox() then return end
    local _, humanoid, root = self:_character()
    if not humanoid or root.Anchored or humanoid.SeatPart or self.active.fly or self.active.freecam then return end
    if humanoid.FloorMaterial ~= self.engine.Enum.Material.Air then self.airborneJumps = 0; return end
    if not self.active.air_jump and (not self.active.double_jump or self.airborneJumps >= 1) then return end
    if not Policy.cooldown(self.last, "airborne_jump", self.now(), 0.25) then return end
    self.airborneJumps += 1
    local current = root.AssemblyLinearVelocity
    root:ApplyImpulse(self.engine.Vector3.new(0, math.max(0, 48 - current.Y), 0) * math.clamp(root.AssemblyMass, 0, 1000))
    humanoid:ChangeState(self.engine.Enum.HumanoidStateType.Jumping)
end

function Runtime:_step(dt)
    if self.destroyed then return end
    local character, humanoid, root = self:_character()
    if self.currentCharacter and not character then self:_resetCharacter() end
    if character and not self.spawnCFrame then self:_adoptCharacter() end
    if character then
        if humanoid.FloorMaterial ~= self.engine.Enum.Material.Air then self.airborneJumps = 0 end
        if self.active.fly and self.flyVelocity and self.flyVelocity.Parent then
            self.flyVelocity.VectorVelocity = if root.Anchored then self.engine.Vector3.zero else self:_direction(false) * self.values.fly
        end
        if self.active.low_gravity and self.gravityForce and self.gravityForce.Parent then
            self.gravityForce.Force = self.engine.Vector3.new(0, root.AssemblyMass * self.services.Workspace.Gravity * (1 - self.values.low_gravity), 0)
        end
        if self.active.noclip then
            for _, part in character:GetDescendants() do
                if part:IsA("BasePart") then self.collisions[part] = true; self.characterLease:write(part, "CanCollide", false) end
            end
        end
        for _, item in {{"speed", humanoid.WalkSpeed == self.values.speed}, {"freeze", root.Anchored == true}} do
            if self.active[item[1]] and not item[2] then self.status[item[1]] = "Local value changed by the game; server may have corrected it" end
        end
    end
    local camera = self.services.Workspace.CurrentCamera
    if camera and camera~=self.currentCamera then
        self.currentCamera=camera
        if self.active.freecam then self:_enableCamera("freecam",true)
        elseif self.active.spectate then self:_enableCamera("spectate",true,self.targets.spectate)
        elseif self.active.fov then self:_enableCamera("fov",true) end
    end
    if self.active.freecam and camera and self.freecamPosition then
        local E, input = self.engine, self.services.UserInputService
        if input:IsMouseButtonPressed(E.Enum.UserInputType.MouseButton2) then
            self.inputLease:write(input, "MouseBehavior", E.Enum.MouseBehavior.LockCurrentPosition)
            local delta = input:GetMouseDelta()
            self.yaw -= delta.X * 0.003
            self.pitch = math.clamp(self.pitch - delta.Y * 0.003, -1.5, 1.5)
        else self.inputLease:restore(input, "MouseBehavior") end
        self.freecamPosition += self:_direction(false) * self.values.freecam * math.clamp(dt, 0, 0.1)
        local frame = E.CFrame.new(self.freecamPosition) * E.CFrame.Angles(0, self.yaw, 0) * E.CFrame.Angles(self.pitch, 0, 0)
        self.cameraLease:write(camera, "CFrame", frame)
        self.cameraLease:write(camera, "Focus", frame * E.CFrame.new(0, 0, -20))
    elseif self.active.spectate then
        local target = self.services.Players:GetPlayerByUserId(self.targets.spectate)
        local subject = target and target.Character and target.Character:FindFirstChildOfClass("Humanoid")
        if not subject or subject.Health <= 0 then
            self.active.spectate = false
            self:_restoreCamera()
            if self.active.fov and camera then self.cameraLease:write(camera, "FieldOfView", self.values.fov) end
            self.status.spectate = "Selected player left or respawned; camera restored"
        elseif camera and camera.CameraSubject ~= subject then self.cameraLease:write(camera, "CameraSubject", subject) end
    end
    if self.now() - self.publishedAt >= 1 then self.publishedAt = self.now(); self:_publish() end
end

function Runtime:start()
    if self.started or self.destroyed then return self end
    self.started = true
    self:_adoptCharacter()
    table.insert(self.connections, self.player.CharacterRemoving:Connect(function() self:_resetCharacter() end))
    table.insert(self.connections, self.player.CharacterAdded:Connect(function()
        self.spawnCFrame = nil
        self:_adoptCharacter()
    end))
    table.insert(self.connections, self.services.RunService.RenderStepped:Connect(function(dt) self:_step(dt) end))
    table.insert(self.connections, self.services.UserInputService.JumpRequest:Connect(function() self:_jump() end))
    if self.transport and type(self.transport.subscribe) == "function" then
        self.unsubscribe = self.transport:subscribe(function(command) return self:dispatch(command) end)
    end
    self:_publish()
    return self
end

function Runtime:destroy()
    if self.destroyed then return end
    self.destroyed = true
    self:_resetCharacter()
    self:_restoreCamera()
    self.lightLease:restoreAll()
    for id in self.objects do self:_remove(id) end
    for _, connection in self.connections do connection:Disconnect() end
    self.connections = {}
    if type(self.unsubscribe) == "function" then pcall(self.unsubscribe) end
    self.started = false
    for _, entry in Catalog.entries do self.active[entry.id]=false; self.status[entry.id]="Stopped" end
    self:_publish()
end

return Runtime
