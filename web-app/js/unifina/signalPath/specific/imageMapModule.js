
SignalPath.ImageMapModule = function(data, canvas, prot) {
	prot = prot || {};
	var pub = SignalPath.AbstractMapModule(data, canvas, prot)
	
	prot.createMap = function(mapOptions) {
		return new StreamrMap(container, mapOptions)
	}
}