package com.unifina.signalpath.custom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class CompilationErrorMessage extends HashMap<String, Object> {
	private final List<Map> errors = new ArrayList<>();
	
	CompilationErrorMessage() {
		this.put("type","compilationErrors");
		this.put("errors",errors);
	}

	CompilationErrorMessage(long line, String message) {
		this();
		addError(line, message);
	}
	
	void addError(long line, String message) {
		HashMap<String,Object> e = new HashMap<>();
		e.put("line", line);
		e.put("msg", message);
		errors.add(e);
	}
}
