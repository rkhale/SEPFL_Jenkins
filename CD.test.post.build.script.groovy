// This code should be added in the Groovy post build section
// This is generic code that is for the Continues delivery jobs Smoke & Full Run
// This will do the following thing
// 1. Retrieve the Name of the JOB.
// 2. If the passing % > than a pre-defined pass % it will trigger a downstream job (if the downstream job exists)
// 3. It will update the Description & the Display Name which is passed.
// Written by Rohan Khale.


import hudson.model.Hudson
import hudson.model.*

def jobNamePassed
def minPassPercentage = 98
def currentHealthScore
def triggerDownStreamJob = "true"
def sepFullBuild
def sepShortBuild


def getCurrentJobName () {
	// This function returns the name of the current Job
	// This returns something like 'Thread[Executor #0 for 192.168.1.10 : executing Test with a Space in the Name #123,5,main].'
	// Now you need to split this & extract only the Job name.
	def build = Thread.currentThread().toString()	
	def jobName = build.split("executing")
	jobName = jobName[1].split("#")
	jobName = jobName[0]
	if (jobName.size()!=0){
		jobName = jobName.trim()
		return "${jobName}"
	}
	return null
}


def getSEPBuildFullStrfromJobName (JOBNAME){
	// Taking the JobName as in input, this function will retrive the SEP Full Build which is present in the current workspace of the Job in junit.xml.
	// The Full build is something like this 14.0.1 (14.0 RU1) build 2582 (14.0.2582.1000)
	// If it doesn't find the file, it will return null
	def returnStr
	def BuildNumberxml
	def currentWorkspace = hudson.model.Hudson.instance.getJob(JOBNAME).lastBuild.workspace;
	def thisJob = hudson.model.Hudson.instance.getItem("${JOBNAME}") 		
	def thisJoblastBuildNumber = thisJob.getNextBuildNumber() - 1		
	def latestJunitResult = "${currentWorkspace}/latest_junit_result.xml"	
	manager.listener.logger.println ("\nRetriving the SEP Full Build String for JOB : ${JOBNAME} Build #${thisJoblastBuildNumber}\n\tWorkspace - ${currentWorkspace}")
	println ("\nRetriving the SEP Full Build String for JOB : ${JOBNAME} Build #${thisJoblastBuildNumber}\n\tWorkspace - ${currentWorkspace}")
	File file = new File(latestJunitResult)
	if (!file.exists()){
		manager.listener.logger.println "File latestJunitResult doesn't exist"
		println "File latestJunitResult doesn't exist"
		returnStr = null
		return null
	}
	else {
		file.eachLine {
			line ->
				if (line.trim().size() == 0){										
					return null
				}
				if (line.contains('SEP Build Installed')){
					BuildNumberxml = line
					BuildNumberxml = BuildNumberxml.trim()
					return true
				}			
		}
		if (BuildNumberxml?.trim()){
			returnStr = BuildNumberxml.split('value=')
			returnStr = returnStr[1].split('/>')
			returnStr = returnStr[0]
			returnStr = returnStr.substring(1, returnStr.length()-1)
			returnStr = returnStr.trim()			
		}
	}
	manager.listener.logger.println ("SEP Full Build String : ${returnStr}\n")
	println ("SEP Full Build String : ${returnStr}\n")
	return returnStr
}


def getSEPBuildShortStrFromSEPFULLBUILD (SEPFULLBUILD){
	// Given the SEP Full Build , this function will retrive the build number in xx.x.xxxx.xxxx . e.g. 14.0.2668.1001
	def returnStr
	def contentRegex = "\\d+.\\d+.\\d+.\\d+"
	if (SEPFULLBUILD?.trim()){
		def contentMatcher = ( SEPFULLBUILD =~ contentRegex )
		returnStr = contentMatcher[0]
		returnStr = returnStr.trim()
	}	
	manager.listener.logger.println ("SEP Short Build String : ${returnStr}\nRetrived this from the Full Build string : ${SEPFULLBUILD}\n")
	println ("SEP Short Build String : ${returnStr}\nRetrived this from the Full Build string : ${SEPFULLBUILD}\n")
	return returnStr
}


def getTestResultHealthScorefromJobName (JOBNAME){
	// This will return the Test Result health score.
	// We are forced to retrive & calculate the Healtscore ourself because, the healthscrore is not calculate till the job is finished.
	def currentWorkspace = hudson.model.Hudson.instance.getJob(JOBNAME).lastBuild.workspace;
	def thisJob = hudson.model.Hudson.instance.getItem("${JOBNAME}") 		
	def thisJoblastBuildNumber = thisJob.getNextBuildNumber() - 1	
	def latestJunitResult = "${currentWorkspace}/latest_junit_result.xml"
	def healthscore
	manager.listener.logger.println ("Retriving the Test case passing % for JOB : ${JOBNAME} Build #${thisJoblastBuildNumber}\n\tWorkspace : ${currentWorkspace}")
	println ("Retriving the Test case passing % for JOB : ${JOBNAME} Build #${thisJoblastBuildNumber}\n\tWorkspace : ${currentWorkspace}")
	File file = new File(latestJunitResult)
	if (!file.exists()){
		manager.listener.logger.println "\tFile latestJunitResult doesn't exist"
		println "\tFile latestJunitResult doesn't exist"
		healthscore = null
		return null
	}
	else {
		file.eachLine {
			line ->
				if (line.trim().size() == 0){
					healthscore = null
					return null
				}
				if (line.contains('testsuites name="LIGHTHOUSE" passed=')){
					BuildNumberxml = line				
					BuildNumberxml=BuildNumberxml.trim()
					return true
				}			
		}
		manager.listener.logger.println("\tFull string from JUNIT XML is : ${BuildNumberxml}")
		println("\tFull string from JUNIT XML is : ${BuildNumberxml}")
		if (BuildNumberxml?.trim()){
			def strInteger = BuildNumberxml.split('testsuites name="LIGHTHOUSE" passed="')		
			strInteger = strInteger[1].split('" failures="')
			strInteger = strInteger[0]
			def intPassed = strInteger.toInteger()	
			
			strInteger = BuildNumberxml.split('failures="')	
			strInteger = strInteger[1].split('" skipped="')
			strInteger = strInteger[0]
			def intFailed = strInteger.toInteger()
			
			strInteger = BuildNumberxml.split('skipped="')	
			strInteger = strInteger[1].split('" tests="')
			strInteger = strInteger[0]
			def intSkipped = strInteger.toInteger()
				
			healthscore = Math.floor(((intPassed)/(intPassed+intFailed+intSkipped))*100) // in case of 99.57 => math.floor will give 99 & not 100 as in case of Math.round	
			
			manager.listener.logger.println ("\t\tPassed Count\t: ${intPassed}\n\t\tFailed Count\t: ${intFailed}\n\t\tSkipped Count\t: ${intSkipped}\n\t\tSum\t\t: ${(intPassed+intFailed+intSkipped)}\n\t\tPassing (%)\t: ${healthscore}%\n")
			println ("\t\tPassed Count\t: ${intPassed}\n\t\tFailed Count\t: ${intFailed}\n\t\tSkipped Count\t: ${intSkipped}\n\t\tSum\t\t: ${(intPassed+intFailed+intSkipped)}\n\t\tPassing (%)\t: ${healthscore}%\n")
		}			
	}
	return healthscore
}

def setBuildDisplayNameDescfromJobName(JOBNAME,BUILD_DISPLAYNAME,BUILD_NUM,CURR_HEALTH,MIN_HEALTH,TRIGGER_DOWNSTREAM_BUILD){
	// This function sets the Build Display Name , Build Description Build Icon & the Build Summary based on the current Health & the min expected Health score.
	def thisJob = hudson.model.Hudson.instance.getItem("${JOBNAME}") 
	def jobFullName = thisJob.getFullName()
	def thisJoblastBuildNumber = thisJob.getNextBuildNumber() - 1
	def thisBuild = thisJob.getBuildByNumber(thisJoblastBuildNumber)
	def thisProject = hudson.model.Hudson.getInstance().getItemByFullName(jobFullName)
	def passIcon = "/var/lib/jenkins/plugins/modernstatus/16x16/blue.png"
	def failIcon = "/var/lib/jenkins/plugins/modernstatus/16x16/red.png"
	def summaryIconPass = "/var/lib/jenkins/plugins/modernstatus/32x32/blue.png"
	def summaryIconFail = "/var/lib/jenkins/plugins/modernstatus/32x32/red.png"
	def buildIcon
	def summaryIcon
	def summaryColor	
	
	def buildDisplayName = BUILD_DISPLAYNAME	
	buildDisplayName = buildDisplayName.trim()
	manager.listener.logger.println ("\nSetting the Build Display name - ${buildDisplayName}")
	println ("\nSetting the Build Display name - ${buildDisplayName}")
	thisBuild.setDisplayName(buildDisplayName)
	
	def buildDescription = "Jenkins Build #${thisJoblastBuildNumber} completed.\nTests executed on SEP BUILD '${BUILD_NUM}'."
	buildDescription = buildDescription.trim()
	
	if (CURR_HEALTH >= MIN_HEALTH){
		summaryColor = "green"
		def downStreamJob = thisProject.getDownstreamProjects()
		if (!downStreamJob.empty && TRIGGER_DOWNSTREAM_BUILD.equals("true")){
			def downStreamJobName = downStreamJob[0].getName()
			def downStreamJobBuildNumber = downStreamJob[0].getNextBuildNumber()
			buildDescription = buildDescription + "\nDownstream Build - ${downStreamJobName} - Build #${downStreamJobBuildNumber} will be triggered."			
			if (CURR_HEALTH!= 100){
				manager.listener.logger.println("Triggering DownStream Job ${downStreamJobName} Build #${downStreamJobBuildNumber}.")
				println("Triggering DownStream Job ${downStreamJobName} Build ${downStreamJobBuildNumber}.")
				hudson.model.Hudson.instance.queue.schedule(downStreamJob)
			}
		}
		// Add ICONS to build runs Manager.addBadge works
		manager.listener.logger.println ("Setting the Green Tick mark to the Build #${thisJoblastBuildNumber} since the Health is ≥ ${MIN_HEALTH}")
		println ("Setting the Green Tick mark to the Build #${thisJoblastBuildNumber} since the Health is ≥ ${MIN_HEALTH}%")
		buildIcon = passIcon
		summaryIcon = summaryIconPass
	}
	else {
		// Build health is not greater than MIN_HEALTH
		// Add ICONS to build runs
		summaryColor = "red"
		buildDescription = buildDescription + "\n"
		manager.listener.logger.println ("Setting the Red exclamation mark to the Build #${thisJoblastBuildNumber} since the Health is < ${MIN_HEALTH}")
		println ("Setting the Red exclamation mark to the Build #${thisJoblastBuildNumber} since the Health is < ${MIN_HEALTH}%")
		buildIcon = failIcon
		summaryIcon = summaryIconFail
	}
	
	//Commenting the code below because it causes the build result to look UGLY
	//manager.listener.logger.println ("Setting the Build text to ${CURR_HEALTH}%")
	//println ("Setting the Build text to ${CURR_HEALTH}%")	
	//manager.addShortText("${CURR_HEALTH}% ", "grey", "white", "0px", "white")
	
	// Figure out a way to retrive the baseurl & the job url.. with that you can link the Summary to Test results.
	//manager.createSummary("${summaryIcon}").appendText("<h3><a href='https://www.w3schools.com'>${CURR_HEALTH}% of all Tests executed on build ${buildDisplayName} have passed.</a></h3>", false, false, false, "${summaryColor}")
	manager.createSummary("${summaryIcon}").appendText("<h3>${CURR_HEALTH}% of all Tests executed on build ${buildDisplayName} have passed.</h3>", false, false, false, "${summaryColor}")
	manager.addBadge("${buildIcon}", "${CURR_HEALTH}% Test Passed")
	
	manager.listener.logger.println ("Setting the Build Description\n${buildDescription}")
	println ("Setting the Build Description\n${buildDescription}")
	thisBuild.setDescription(buildDescription)	
	
	return
}


// MAIN
jobNamePassed = getCurrentJobName()
if (jobNamePassed?.trim()){
	manager.listener.logger.println("\nCurrent Job name is : ${jobNamePassed}.")
	println("\nCurrent Job name is : ${jobNamePassed}.")
}

// Below is for Continues Delivery Model
sepFullBuild = getSEPBuildFullStrfromJobName (jobNamePassed)
sepShortBuild = getSEPBuildShortStrFromSEPFULLBUILD (sepFullBuild)
currentHealthScore = getTestResultHealthScorefromJobName (jobNamePassed)

manager.listener.logger.println ("Passing the following parameters\n\tJob Name : ${jobNamePassed}\n\tSEP Build Full : ${sepFullBuild}\n\tSEP Build Short : ${sepShortBuild}\n\tBuild Current Health : ${currentHealthScore}%\n\tMin Passing Health : ${minPassPercentage}%\n\tTrigger DownStream Build (true/false) : ${triggerDownStreamJob}")
println ("Passing the following parameters\n\tJob Name : ${jobNamePassed}\n\tSEP Build Full : ${sepFullBuild}\n\tSEP Build Short : ${sepShortBuild}\n\tBuild Current Health : ${currentHealthScore}%\n\tMin Passing Health : ${minPassPercentage}%\n\tTrigger DownStream Build (true/false) : ${triggerDownStreamJob}")
setBuildDisplayNameDescfromJobName (jobNamePassed , sepShortBuild , sepFullBuild , currentHealthScore , minPassPercentage , triggerDownStreamJob )