--!strict
-- Pure validation; no Roblox globals so the policy is executable in the Luau CLI.
local Policy = {}
function Policy.finite(value: any): boolean
    return type(value) == "number" and value == value and value > -math.huge and value < math.huge
end
function Policy.validate(raw: any, catalog: any): (any?, string?)
    if type(raw) ~= "table" then return nil, "Expected a command object." end
    if type(raw.id) ~= "string" or #raw.id > 48 then return nil, "Invalid module ID." end
    local entry = catalog.byId[raw.id]
    if not entry then return nil, "Unknown module." end
    if entry.kind == "toggle" and type(raw.enabled) ~= "boolean" then return nil, "Toggle requires enabled=true or false." end
    if raw.requestId ~= nil and (type(raw.requestId) ~= "string" or #raw.requestId > 80) then return nil, "Invalid request ID." end
    local command = {id=raw.id, enabled=raw.enabled == true, requestId=raw.requestId}
    if entry.min then
        local value = if raw.value == nil then entry.default else raw.value
        if not Policy.finite(value) then return nil, "Value must be a finite number." end
        command.value = math.clamp(value, entry.min, entry.max)
    end
    if raw.targetUserId ~= nil then
        if not Policy.finite(raw.targetUserId) or raw.targetUserId % 1 ~= 0 or raw.targetUserId == 0 then return nil, "Invalid player UserId." end
        command.targetUserId = raw.targetUserId
    end
    if (raw.id == "friend_fling" or (raw.id == "spectate" and raw.enabled)) and not command.targetUserId then
        return nil, "Choose a player first."
    end
    return command, nil
end
function Policy.spend(bucket: any, now: number, cost: number?): boolean
    local spend = cost or 1
    bucket.tokens = math.min(20, bucket.tokens + math.max(0, now - bucket.updated) * 10)
    bucket.updated = now
    if bucket.tokens < spend then return false end
    bucket.tokens -= spend
    return true
end
function Policy.cooldown(last: any, id: string, now: number, interval: number): boolean
    if last[id] and now - last[id] < interval then return false end
    last[id] = now
    return true
end
return Policy
