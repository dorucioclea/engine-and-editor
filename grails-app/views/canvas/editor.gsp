<html>
	<head>
		<meta name="layout" content="sidemenu" />

		<title>Canvas</title>

		<r:require module='jstree'/>
		<r:require module='jquery-ui'/>
		<r:require module="bootbox"/>
		<r:require module="bootstrap-contextmenu"/>
		<r:require module="bootstrap-datepicker"/>
		<r:require module="slimscroll"/>
		<r:require module="search-control"/>
		<r:require module="signalpath-browser"/>
		<r:require module="signalpath-theme"/>
		<r:require module="hotkeys"/>
		<r:require module="touchpunch"/>
		<r:require module="detect-timezone"/>
		<r:require module="canvas-controls"/>

		<r:script>

// Make the loadBrowser global to allow apps that use this plugin to extend it by adding tabs
var loadBrowser

$('#moduleTree').bind('loaded.jstree', function() {
	Tour.autoStart()
})

$(document).ready(function() {

	function settings() {
		var tz = jstz();

		return {
			beginDate: $("#beginDate").val(),
			endDate: $("#endDate").val(),
			speed: $("#speed").val(),
			timeOfDayFilter: {
				timeOfDayStart: $("#timeOfDayStart").val(),
				timeOfDayEnd: $("#timeOfDayEnd").val(),
				timeZone: tz.timezone_name,
				timeZoneOffset: tz.utc_offset,
				timeZoneDst: tz.uses_dst
			},
			editorState: {
				runTab: $("#tab-historical").is(":visible") ? "#tab-historical" : "#tab-realtime"
			}
		}
	}

	SignalPath.init({
		parentElement: $('#canvas'),
		settings: settings,
		errorHandler: function(data) {
			Streamr.showError(data.msg)
		},
		notificationHandler: function(data) {
			Streamr.showInfo(data.msg)
		},
		connectionOptions: {
			server: "${grailsApplication.config.streamr.ui.server}",
			autoConnect: false,
			autoDisconnect: true
		}
	});

	$(SignalPath).on('loading', function() {
		$('#modal-spinner').show()
	})

	$(SignalPath).on('loaded', function(event, json) {
		$('#modal-spinner').hide()

		var settings = json.settings
		if (settings.beginDate) {
			$("#beginDate").val(settings.beginDate).trigger("change")
		}
		if (settings.endDate)
			$("#endDate").val(settings.endDate).trigger("change")

		if (settings.timeOfDayFilter) {
			$("#timeOfDayStart").val(settings.timeOfDayFilter.timeOfDayStart).trigger("change")
			$("#timeOfDayEnd").val(settings.timeOfDayFilter.timeOfDayEnd).trigger("change")
		}

		$("#speed").val(settings.speed!=null ? settings.speed : 0).trigger("change")

		if (settings.editorState && settings.editorState.runTab)
			$("a[href="+settings.editorState.runTab+"]").tab('show')

	});

	// Show realtime tab when a running SignalPath is loaded
	$(SignalPath).on('loaded', function(event, json) {
		if (SignalPath.isRunning()) {
			$("a[href=#tab-realtime]").tab('show')
		}
	});

	// Try to ping a running SignalPath on load, and show error if it can't be reached
	$(SignalPath).on('loaded', function(event, json) {
		if (SignalPath.isRunning()) {
			SignalPath.sendRequest(undefined, {type:"ping"}, function(response, err) {
				if (err)
					Streamr.showError('${message(code:'canvas.ping.error')}')
			})
		}
	});

	$(SignalPath).on('error', function(error) {
		console.error(error)
		$('#modal-spinner').hide()
	})

	$(SignalPath).on('saving', function() {
		$('#modal-spinner').show()
	})

	$(SignalPath).on('saved', function(event, savedJson) {
		$('#modal-spinner').hide()
		Streamr.showSuccess('${message(code:"signalpath.saved")}: '+savedJson.name)
	})

	// show search control
	new SearchControl(
		'${ createLink(controller: "stream", action: "search") }',
		'${ createLink(controller: "module", action: "jsonGetModules") }',
		$('#search'))
	
	// Bind slimScroll to main menu
    $('#main-menu-inner').slimScroll({
      height: '100%'
    })

	loadBrowser = new SignalPathBrowser()
		.tab('My Canvases', '${ createLink(controller: "canvas", \
			action: "loadBrowser", params: [ browserId: "archiveLoadBrowser" ]) }')

		.tab('Examples', '${ createLink(controller: "canvas", \
			action: "loadBrowser", params: [ browserId: "examplesLoadBrowser" ]) }')
			
		.onSelect(function(id) {
			SignalPath.load(id)
		})

	<%-- Show examples loader if requested --%>
	<g:if test="${examples}">
		// Use a timeout to allow extensions to register their tabs before the loadBrowser is shown
		setTimeout(function() {
			loadBrowser.modal()
			loadBrowser.show("Examples")
		}, 0)
	</g:if>

	$('#newSignalPath').click(function() {
		SignalPath.clear()
	})

	$('#loadSignalPath').click(function() {
		loadBrowser.modal()
	})

	$(document).bind('keyup', 'alt+r', function() {
		SignalPath.run();
	});

	$('#csv').click(function() {
		var ctx = {
			csv: true,
			csvOptions: {
				timeFormat: $("#csvTimeFormat").val(),
				separator: $("#csvSeparator").val(),
				filterEmpty: $("#csvFilterEmpty").attr("checked") ? true : false,
				lastOfDayOnly: $("#csvLastOfDayOnly").attr("checked") ? true : false
			}
		}

		SignalPath.run(ctx);
	});

	// Historical run button
	var historicalRunButton = new CanvasStartButton({
		el: $("#run-historical-button"),
		signalPath: SignalPath,
        settings: settings,
        startContent: '<i class="fa fa-play"></i> Run',
        stopContent: '<i class="fa fa-spin fa-spinner"></i> Abort',
        adhoc: true,
        clearState: true
	})

	// Realtime run button
	var realtimeRunButton = new CanvasStartButton({
		el: $("#run-realtime-button"),
		signalPath: SignalPath,
        settings: settings,
        startContent: '<i class="fa fa-play"></i> Start',
        stopContent: '<i class="fa fa-stop"></i> Stop',
        adhoc: false,
        clearState: false
	})
	realtimeRunButton.on('start-confirmed', function() {
		Streamr.showSuccess('${message(code:"canvas.started")}'.replace('{0}', SignalPath.getName()))
	})
	realtimeRunButton.on('stop-confirmed', function() {
		Streamr.showSuccess('${message(code:"canvas.stopped")}'.replace('{0}', SignalPath.getName()))
	})
	realtimeRunButton.on('stop-error', function() {
		Streamr.showError('${message(code:"canvas.stop.error")}')
	})

	// Run and clear link
	var realtimeRunAndClearButton = new CanvasStartButton({
		el: $("#run-realtime-button"),
		signalPath: SignalPath,
        settings: settings,
        startContent: '<i class="fa fa-play"></i> Start',
        stopContent: '<i class="fa fa-stop"></i> Stop',
        adhoc: false,
        clearState: true,
        clickElement: $("#run-realtime-clear")
	})
	realtimeRunAndClearButton.on('start-confirmed', function() {
		Streamr.showSuccess('${message(code:"canvas.clearAndStarted")}: '.replace('{0}', SignalPath.getName()))
	})

	new CanvasNameEditor({
		el: $("#canvas-name-editor"),
		signalPath: SignalPath
	})

	$(SignalPath).on('new', function(e, json) {
		$("#share-button").attr("disabled", "disabled")
	})

	$(SignalPath).on('loaded saved', function(e, json) {
		var canvasUrl = Streamr.createLink({uri: "api/v1/canvases/" + json.id})
		// Check share permission by knocking on the /permissions/ API endpoint
		// Disabled without .forbidden means not checked yet
		$("#share-button").attr("disabled", "disabled")
		$("#share-button").removeClass("forbidden")
		$.getJSON(canvasUrl + "/permissions").success(function () {
			$("#share-button").data("url", canvasUrl)
			$("#share-button").removeAttr("disabled")
		}).fail(function () {
			// Forbidden means no permission
			$("#share-button").addClass("forbidden")
		})
	})

	<g:if test="${id}">
		SignalPath.load('${id}');
	</g:if>
})

$(document).unload(function () {
	SignalPath.unload()
})

</r:script>

<!-- mustache templates -->
<g:render template="/templates/remote-tabs" plugin="unifina-core"/>

<!-- extension point for apps using the core plugin -->
<g:render template="/canvas/signalPathExtensions"/>
<g:render template="/canvas/buildHeadExtensions"/>

</head>

<body class="canvas-editor-page main-menu-fixed ${embedded ? 'embedded' : ''}">
	<div id="main-menu" role="navigation">
		<div id="main-menu-inner">
			<div id="toolbar-buttons" class="menu-content" style="overflow: visible;">
				<div class="btn-group load-save-group">
					<button id="newSignalPath" title="New Canvas" class="btn btn-default">
						<i class="fa fa-file-o"></i>
					</button>

					<button id="loadSignalPath" title="Load Canvas" class="btn btn-default">
						<i class="fa fa-folder-open"></i>
					</button>
		
					<sp:saveButtonDropdown/>
				</div>
			</div>

			<div class="menu-content">

				<ul class="nav nav-tabs nav-justified nav-tabs-xs run-mode-tabs">
					<li class="active">
						<a href="#tab-historical" role="tab" data-toggle="tab">Historical</a>
					</li>
					<li class="">
						<a href="#tab-realtime" role="tab" data-toggle="tab">Realtime</a>
					</li>
				</ul>

				<div class="tab-content">
					<!-- Historical run controls -->
					<div role="tabpanel" class="tab-pane active" id="tab-historical">

						<form onsubmit="return false;">
							<g:hiddenField name="defaultBeginDate" value="${formatDate(date:beginDate,format:"yyyy-MM-dd")}"/>
							<g:hiddenField name="defaultEndDate" value="${formatDate(date:endDate,format:"yyyy-MM-dd")}"/>

							<div class="form-group form-group-period">
								<div class="input-group">
									<span class="input-group-addon">From</span>
									<ui:datePicker name="beginDate" value="${beginDate}" class="form-control"/>
								</div>
								<div class="input-group">
									<span class="input-group-addon">To</span>
									<ui:datePicker name="endDate" value="${endDate}" class="form-control"/>
								</div>
								<a href="#" id="historical-options-button" class="btn btn-primary btn-outline dark btn-xs pull-right" title="Historical Run Options" data-toggle="modal" data-target="#historicalOptionsModal">
									<i class="fa fa-cog"></i>
									Options
								</a>
							</div>

							<div class="btn-group btn-block run-group">
								<button id="run-historical-button" class="btn btn-primary col-xs-10 run-button">
									<i class="fa fa-play"></i>
									Run
								</button>
								<button id="runDropdown" type="button" class="btn btn-primary col-xs-2 dropdown-toggle"
										data-toggle="dropdown">
									<span class="caret"></span>
									<span class="sr-only">Toggle Dropdown</span>
								</button>
								<ul class="dropdown-menu" role="menu">
									<li><a id="csvModalButton" href="#" data-toggle="modal" data-target="#csvModal">Run as CSV export..</a></li>
								</ul>
							</div>
						</form>
					</div>

					<!-- Realtime run controls -->
					<div role="tabpanel" class="tab-pane" id="tab-realtime">
						<div class="menu-content-header">
							<!--<label>Realtime Run Options</label>-->
							<a href="#" id="realtime-options-button" class="btn btn-primary btn-outline dark btn-xs pull-right" title="Realtime Run Options" data-toggle="modal" data-target="#realtimeOptionsModal">
								<i class="fa fa-cog"></i>
								Options
							</a>
						</div>
						<div class="btn-group btn-block run-group">
							<button id="run-realtime-button" class="btn btn-primary col-xs-10 run-button">
								<i class="fa fa-play"></i>
								<g:message code="canvas.start.label"/>
							</button>
							<button id="runDropdown" type="button" class="btn btn-primary col-xs-2 dropdown-toggle"
									data-toggle="dropdown">
								<span class="caret"></span>
								<span class="sr-only">Toggle Dropdown</span>
							</button>
							<ul class="dropdown-menu" role="menu">
								<li><a id="run-realtime-clear" href="#"><g:message code="canvas.clearAndStart.label"/></a></li>
							</ul>
						</div>
					</div>
				</div>
			</div>
			
			<div id="search-control" class="menu-content" style="overflow: visible">
				<label for="search">Add Stream / Module</label><br>
				<input type="text" class="typeahead form-control" id="search" placeholder="Type to search"/>
			</div>

			<div class="menu-content">
				<div class="menu-content-header">
					<label for="moduleTree">Module Browser</label>
				</div>
				
				<sp:moduleBrowser id="moduleTree" buttonId="addModule" />
				
				<sp:moduleAddButton buttonId="addModule" browserId="moduleTree" class="btn-block">
					<i class="fa fa-plus"></i>
					<g:message code="signalPath.addModule.label" default="Add Module" />
				</sp:moduleAddButton>
			</div>

			<div class="menu-content">
				<ui:shareButton id="share-button" class="btn-block" getName="SignalPath.getName()" disabled="disabled"> Share </ui:shareButton>
			</div>

		</div> <!-- / #main-menu-inner -->
	</div> <!-- / #main-menu -->

	<div id="content-wrapper">
		<ui:breadcrumb>
			<li class="active">
				<span id="canvas-name-editor"></span>
			</li>
		</ui:breadcrumb>
		<div id="canvas" class="scrollable embeddable"></div>
	</div>

	<div id="main-menu-bg"></div>

	<div id="csvModal" class="modal fade">
	  <div class="modal-dialog">
	    <div class="modal-content">
	      <div class="modal-header">
	        <button type="button" class="close" data-dismiss="modal"><span aria-hidden="true">&times;</span><span class="sr-only">Close</span></button>
	        <h4 class="modal-title">CSV Export Options</h4>
	      </div>
	      <div class="modal-body">
				<div class="form-group">
					<label>Time Format</label>
					
					<select id="csvTimeFormat" class="form-control">
						<option value="1">Java timestamp (milliseconds since January 1st 1970 UTC)</option>
						<option value="2" selected>ISO 8601 in your timezone</option>
						<option value="3">ISO 8601 in UTC</option>
					</select>
					
					<label>Separator</label>
					
					<select id="csvSeparator" class="form-control">
						<option value="," selected>Comma (,)</option>
						<option value=";">Semicolon (;)</option>
						<option value="tab">Tab</option>
					</select>
					
					<label>Filters</label>
					
					<div class="checkbox">
						<label>
							<input type="checkbox" id="csvFilterEmpty" value="true" checked/>
							Require data in all columns
						</label>
					</div>
					
					<div class="checkbox">
						<label>
							<input type="checkbox" id="csvLastOfDayOnly" value="false"/>
							Last row of day only
						</label>
					</div>
				</div>
	      </div>
	      <div class="modal-footer">
	        <button type="button" class="btn btn-default" data-dismiss="modal">Cancel</button>
	        <button id="csv" class="btn btn-primary" data-dismiss="modal">Run</button>
	      </div>
	    </div><!-- /.modal-content -->
	  </div><!-- /.modal-dialog -->
	</div><!-- /.modal -->
	
	<div id="historicalOptionsModal" class="modal fade">
	  <div class="modal-dialog">
	    <div class="modal-content">
	      <div class="modal-header">
	        <button type="button" class="close" data-dismiss="modal"><span aria-hidden="true">&times;</span><span class="sr-only">Close</span></button>
	        <h4 class="modal-title">Historical Run Options</h4>
	      </div>
	      <div class="modal-body">
				<div class="form-group">
					<label>Speed</label>
					<select id="speed" class="form-control">
						<option value="0">Full</option>
						<option value="1">1x</option>
						<option value="10">10x</option>
						<option value="100">100x</option>
						<option value="1000">1000x</option>
					</select>
				</div>
				
				<div class="form-group">
					<label>Speed time of day</label>
					<input id="timeOfDayStart" type="text" name="timeOfDayStart" value="00:00:00" class="form-control">
					<input id="timeOfDayEnd" type="text" name="timeOfDayEnd" value="23:59:00" class="form-control">
				</div>

	      </div>
	      <div class="modal-footer">
	        <button type="button" class="btn btn-primary" data-dismiss="modal">OK</button>
	      </div>
	    </div><!-- /.modal-content -->
	  </div><!-- /.modal-dialog -->
	</div><!-- /.modal -->

	<div id="realtimeOptionsModal" class="modal fade">
		<div class="modal-dialog">
			<div class="modal-content">
				<div class="modal-header">
					<button type="button" class="close" data-dismiss="modal"><span aria-hidden="true">&times;</span><span class="sr-only">Close</span></button>
					<h4 class="modal-title">Realtime Run Options</h4>
				</div>
				<div class="modal-body">

				</div>
				<div class="modal-footer">
					<button type="button" class="btn btn-primary" data-dismiss="modal">OK</button>
				</div>
			</div><!-- /.modal-content -->
		</div><!-- /.modal-dialog -->
	</div><!-- /.modal -->

	<ul id="save-dropdown-menu" class="dropdown-menu" role="menu">
		<li class="disabled"><a href="#" id="saveButton">Save</a></li>
		<li><a href="#" id="saveAsButton">Save as..</a></li>
	</ul>
	
	<g:render template="/feedback/fixedFeedback" plugin="unifina-core"/>

	<!-- extension point for apps using the core plugin -->
	<g:render template="/canvas/buildBodyExtensions"/>

</body>
</html>
