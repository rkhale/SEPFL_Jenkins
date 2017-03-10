// This code should be added in the Groovy post build section
// This is generic code that is for the Continues Integration jobs Smoke & Full Run
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
def sepChangeList
def sepChangeListFile = "/mnt/CI_DEV_BUILDS/change.properties"


def getCurrentJobName () {
	// This function gets the name of the current Job
	def build = Thread.currentThread().toString()
	// This returns something like 'Thread[Executor #0 for 192.168.1.10 : executing Test with a Space in the Name #123,5,main].'
	// Now you need to split this & extract only the Job name.
	def jobName = build.split("executing")
	jobName = jobName[1].split("#")
	jobName = jobName[0]
	if (jobName.size()!=0){
		jobName = jobName.trim()		
		return "${jobName}"
	}
	return null
}


def getChanglistNumberfromJobName (JOBNAME , CHANGLIST_FILE){
	// This will retrive the changlist number, which is currently prsent at /mnt/CI_DEV_BUILDS/change.properties	
	def returnStr	
	//def currentWorkspace = hudson.model.Hudson.instance.getJob(JOBNAME).lastBuild.workspace	// This code will be active once we also copy the change.properties to the workspace
	//def changlistfile = "${currentWorkspace}/change.properties"
	//def changlistfile = "/mnt/CI_DEV_BUILDS/change.properties"
	def changlistfile = "${CHANGLIST_FILE}"	
	
	def thisJob = hudson.model.Hudson.instance.getItem("${JOBNAME}") 		
	def thisJoblastBuildNumber = thisJob.getNextBuildNumber() - 1
	def thisjobBuilding = thisJob.isBuilding()	
	
	manager.listener.logger.println ("Retriving the Changelist for JOB : ${JOBNAME} Build #${thisJoblastBuildNumber}")
	println ("Retriving the Changlist for JOB : ${JOBNAME} Build #${thisJoblastBuildNumber}")
	
	File file = new File(changlistfile)
	if (!file.exists()){
		manager.listener.logger.println "File ${changlistfile} doesn't exist"
		println "File ${changlistfile} doesn't exist"
		returnStr = null
		return
	}
	else {
		file.eachLine {
			line ->
				if (line.trim().size() == 0){
					returnStr = null
					return null
				}
				if (line.contains('p4.changelist')){
					BuildNumberxml = line				
					return true
				}			
		}
		if (BuildNumberxml?.trim()){
			returnStr = BuildNumberxml.split('=')				
			returnStr = returnStr[1]
			returnStr = returnStr.trim()			
		}		
	}
	manager.listener.logger.println ("\tChanglist : ${returnStr}\n")
	println ("\tChanglist : ${returnStr}\n")
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
	
	def buildDescription = "Jenkins Build #${thisJoblastBuildNumber} completed.\nTests executed on Changelist #${BUILD_NUM}."
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
	manager.createSummary("${summaryIcon}").appendText("<h3>${CURR_HEALTH}% of all Tests executed on Changelist #${buildDisplayName} have passed.</h3>", false, false, false, "${summaryColor}")
	manager.addBadge("${buildIcon}", "${CURR_HEALTH}% Test Passed")
	
	manager.listener.logger.println ("Setting the Build Description\n${buildDescription}")
	println ("Setting the Build Description\n${buildDescription}")
	thisBuild.setDescription(buildDescription)	
	
	return
}


// MAIN
jobNamePassed = getCurrentJobName()
if (jobNamePassed?.trim()){
	manager.listener.logger.println("\nCurrent Job name is : ${jobNamePassed}.\n")
	println("\nCurrent Job name is : ${jobNamePassed}.\n")
}

//Below is for Continues Integration Smoke & Full Run tests.
sepChangeList = getChanglistNumberfromJobName (jobNamePassed,sepChangeListFile)
currentHealthScore = getTestResultHealthScorefromJobName (jobNamePassed)

manager.listener.logger.println ("Passing the following parameters\n\tJob Name : ${jobNamePassed}\n\tSEP Build Short : ${sepChangeList}\n\tSEP Build Full : ${sepChangeList}\n\tBuild Current Health : ${currentHealthScore}%\n\tMin Passing Health : ${minPassPercentage}%\n\tTrigger DownStream Build (true/false) : ${triggerDownStreamJob}")
println ("Passing the following parameters\n\tJob Name : ${jobNamePassed}\n\tSEP Build Short : ${sepChangeList}\n\tSEP Build Full : ${sepChangeList}\n\tBuild Current Health : ${currentHealthScore}%\n\tMin Passing Health : ${minPassPercentage}%\n\tTrigger DownStream Build (true/false) : ${triggerDownStreamJob}")
setBuildDisplayNameDescfromJobName (jobNamePassed , sepChangeList , sepChangeList , currentHealthScore , minPassPercentage , triggerDownStreamJob )