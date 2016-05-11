/**
 *
 */
SignalPath.VariadicInput = function(json, parentDiv, module, type, pub) {
    pub = pub || {};
    pub = SignalPath.Input(json, parentDiv, module, type, pub);

    var super_createDiv = pub.createDiv
    pub.createDiv = function() {
        var div = super_createDiv()

        div.bind("spConnect", function(event, output) {
            if (!SignalPath.isBeingReloaded) {
                console.log("connected")

                if (json.variadic.isLast) {
                    var jsonCopy = jQuery.extend(true, {}, json) // deep-copy object

                    json.variadic.isLast = false
                    json.requiresConnection = true // does not work
                    json.connected = true

                    jsonCopy.connected = false
                    delete jsonCopy.id
                    delete jsonCopy.longName
                    delete jsonCopy.sourceId
                    jsonCopy.name = "endpoint" + Date.now()
                    jsonCopy.displayName = json.displayName.replace(/[0-9]/g, '') + (json.variadic.index + 1)
                    jsonCopy.requiresConnection = false
                    jsonCopy.variadic.isLast = true
                    jsonCopy.variadic.index += 1
                    module.addInput(jsonCopy)
                }
            }
        })

        div.bind("spDisconnect", function(event, output) {
            if (!SignalPath.isBeingReloaded) {
                console.log("disconnected")
                module.removeInput(json.name)
            }
        })

        return div
    }

    return pub;
}