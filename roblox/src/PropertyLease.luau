--!strict
-- Restore only properties still holding the value this companion wrote.
local Lease = {}
Lease.__index = Lease
function Lease.new()
    return setmetatable({records={}}, Lease)
end
function Lease:write(instance: any, property: string, value: any)
    local record = self.records[instance]
    if not record then record = {}; self.records[instance] = record end
    local item = record[property]
    if not item then item = {before=instance[property]}; record[property] = item end
    instance[property] = value
    item.written = value
end
function Lease:restore(instance: any, property: string)
    local record = self.records[instance]
    local item = record and record[property]
    if not item then return end
    pcall(function()
        if instance[property] == item.written then instance[property] = item.before end
    end)
    record[property] = nil
    if next(record) == nil then self.records[instance] = nil end
end
function Lease:restoreAll()
    local instances = {}
    for instance in self.records do table.insert(instances, instance) end
    for _, instance in instances do
        local properties = {}
        for property in self.records[instance] do table.insert(properties, property) end
        for _, property in properties do self:restore(instance, property) end
    end
end
return Lease
