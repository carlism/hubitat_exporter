definition(
    name: "Prometheus Metrics Endpoint",
    namespace: "net.leiby",
    author: "Carl Leiby",
    description: "This app exposes device states and properties as metrics for the prometheus scraper",
    category: "Utility/Data",
    singleInstance: true,
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)
preferences {
    preferences { page(name: "pageConfig") }
}

def pageConfig() {
    dynamicPage(name: "", title: "", install: true, uninstall: true, refreshInterval:0) {
          section ("Devices",hideable:true,hidden:false) {
            input "switches", "capability.switch", title: "Switches", multiple: true, required: false
            input "temperatures", "capability.temperatureMeasurement", title: "Temperature Sensors", multiple: true, required: false
            input "locks", "capability.lock", title: "Locks", multiple: true, required: false
        }

        section ("Request Source",hideable:true,hidden:true) {
            input(name:"requestSource",type:"enum",title:"Allow Requests From",options:[0:"Cloud and Local",1:"Cloud Only",2:"Local Only"],defaultValue:0,required:true)

        }

        if (!state.accessToken) {
            createAccessToken() // create our own OAUTH access token to use in webhook url
        }

		section("URL's",hideable:true,hidden:true) {
            paragraph("<b>Getting metrics locally:</b>\n" +
                      "${getLocalApiServerUrl()}/${app.id}/metrics?access_token=${state.accessToken}\n")
            paragraph("<b>Getting metrics remotely:</b>\n" +
                      "${getApiServerUrl()}/${hubUID}/apps/${app.id}/metrics?access_token=${state.accessToken}\n")
        }

    }
}

mappings {
    path("/metrics") {
        action: [
            GET: "produceMetrics"
        ]
    }
}

def installed() {
    state.installedAt = now()
    state.loggingLevelIDE = 5
    log.debug "${app.label}: Installed with settings: ${settings}"
}

def updated() {
}

def produceMetrics() {
    if (requestSource=="0" || (requestSource=="1" && request?.requestSource=="cloud") || (requestSource=="2" && request?.requestSource=="local")) {
        def resp = ""
        [switches, temperatures, locks].each { collection ->
            collection.each {
                resp += deviceMetrics(it)
            }
        }
        render contentType: "text/plain", data: resp, status: 200
    } else {
        log.debug "Requests from '${request?.requestSource}' are blocked."
    }
}

def deviceMetrics(device) {
    def resp = ""
    [
        ["switch", "on"], ["contact", "closed"], ["lock", "locked"], ["tamper", "tampered"]
    ].each { attribute, onValue ->
        if (device.supportedAttributes.any{ it.name == attribute }) {
            resp += 'hubitat_'+attribute+'{device="'+device.id+'",name="'+device.displayName+'"} ' + (device.currentValue(attribute)==onValue?1:0) + "\n"
        }
    }
    ["level", "temperature", "battery", "energy", "power"].each { attribute ->
        if (device.supportedAttributes.any{ it.name == attribute}) {
            if (device.currentValue(attribute) != null) {
                resp += 'hubitat_'+attribute+'{device="'+device.id+'",name="'+device.displayName+'"} '+device.currentValue(attribute)+"\n"
            }
        }
    }
    // log.debug 'type='+device.supportedAttributes
    return resp
}
