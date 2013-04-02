package com.unifina.signalpath

import grails.converters.JSON
import grails.util.GrailsUtil

import com.unifina.utils.Globals
import com.unifina.utils.GlobalsFactory;

class SavedSignalPathController {
	
	def signalPathService
	def springSecurityService
	def grailsApplication
	
	def createSaveData(SavedSignalPath ssp) {
		return [url:createLink(controller:"savedSignalPath",action:"save",params:[id:ssp.id]), name:ssp.name, target: "Archive id "+ssp.id]
	}
	
	def load() {
		def ssp = SavedSignalPath.get(Integer.parseInt(params.id))		
		Map json = JSON.parse(ssp.json);

		// Reconstruct to bring the path up to date
		json.signalPathData.name = ssp.name
		Globals globals = GlobalsFactory.createInstance([:], grailsApplication)
		Map result = signalPathService.reconstruct(json,globals)
		
		result.saveData = createSaveData(ssp) 
		
		render result as JSON
	}

	def save() {
		SavedSignalPath ssp
		if (params.id)
			ssp = SavedSignalPath.get(params.id)
		else 
			ssp = new SavedSignalPath()
		
		ssp.properties = params

		try {
			Globals globals = GlobalsFactory.createInstance([:], grailsApplication)
			
			// Make sure the name is set
			Map json = JSON.parse(params.json)
			json.signalPathData.name = params.name
			// Rebuild the json to check it's ok and up to date
			SignalPath sp = signalPathService.jsonToSignalPath(json.signalPathData,true,globals,true)
			json.signalPathData = signalPathService.signalPathToJson(sp)
			ssp.json = (json as JSON)

			ssp.hasExports = sp.hasExports()

//			ssp.live = json.signalPathContext.live
			ssp.user = springSecurityService.currentUser
			ssp.save()

			if (ssp.id==null)
				throw new Exception("Internal error: Returned id was null!")
			
			def res = createSaveData(ssp)
			render res as JSON
		} catch (Exception e) {
			e = GrailsUtil.deepSanitize(e)
			Map r = [error:true, message:"SIGNALPATH NOT SAVED:\n"+e.message]
			render r as JSON
		}
	}
	
	def loadBrowser() {
		def ssp = SavedSignalPath.executeQuery("select sp.id, sp.name from SavedSignalPath sp where sp.user = :user order by sp.id desc", [user:springSecurityService.currentUser])
		def result = [signalPaths:[]]
		ssp.each {
			def tmp = [:]
			tmp.id = it[0]
			tmp.name = it[1]
			tmp.url = createLink(controller:"savedSignalPath",action:"load",params:[id:it[0]])
//			tmp.saveData = [url:createLink(controller:"savedSignalPath",action:"save",params:[id:it[0]]), target: "Archive id "+it[0]]
			result.signalPaths.add(tmp)  
		}
		return result
	}
	
}
