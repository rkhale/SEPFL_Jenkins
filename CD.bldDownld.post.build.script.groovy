// This code should be added in the Groovy post build section
// This is generic code that is for the Continues delivery jobs Smoke & Full Run
// This will do the following thing
// 1. Retrieve the Name of the JOB.
// 2. It will update the Description & the Display Name which is passed.
// Written by Rohan Khale.


import hudson.model.Hudson
import hudson.model.*

def jobNamePassed
def jobLog
def sepBuildNumber


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


def getCurrentConsoleLogfromJobName (JOBNAME){
	// This function will return the current builds log.
	def jobName = JOBNAME
	def returnString
	def thisJob = hudson.model.Hudson.instance.getItem("${JOBNAME}")
	def jobFullName = thisJob.getFullName()
	def thisJoblastBuildNumber = thisJob.getNextBuildNumber() - 1
	def thisBuild = thisJob.getBuildByNumber(thisJoblastBuildNumber)
	if (thisBuild.log?.trim()){
		returnString = thisBuild.log
	}	
	return returnString
}


def isBuildSuccessfullyDownloaded (BUILDLOG){
	// This function will determine if the Build was successfully downloaded or not.
	// If the build was successfully downloaded, it will return true else false.
	def returnBool
	def buildLog = BUILDLOG
	def failureMsg = "Latest build has already been tested or No New Testable Build found."
	if (buildLog?.trim()) {
		if (buildLog.contains("${failureMsg}")){			
			returnBool = false
		}
		else {			
			returnBool = true
		}
	}
	else {
		manager.listener.logger.println("\nBuild log empty or is blank.")
		println("\nBuild log empty or is blank.")
		returnStr false
	}
	return returnBool
}


def getBuildNumFromBuildLog (BUILDLOG){
	// This function will retrive the build number from the build log.
	// This will search for string "Latest successful build is	==>	" e.g. "Latest successful build is	==>	ftp://10.0.0.25/unreleased/SEP/14.0/1923"
	// At the end of this string is the build number.
	def returnStr
	def buildLog=BUILDLOG	
	def logLineSuccess = "Latest successful build is	==>	"
	def buildLine = buildLog.split("\n")
	buildLine.any { LINE ->
		if (LINE.contains("${logLineSuccess}")){			
			logLineSuccess = LINE
			return true
		}
		else {
			return
		}
	}
	if (logLineSuccess?.trim()){
		//manager.listener.logger.println("\nFound ${logLineSuccess}")
		//println("\nFound ${logLineSuccess}")
		buildLog = logLineSuccess.split("/")
		returnStr = buildLog[buildLog.size() - 1]
		returnStr = returnStr.trim()		
	}	
	return returnStr
}


def setBuildDisplayNameFromJobName(JOBNAME,BUILD_DISPLAYNAME){
	def thisJob = hudson.model.Hudson.instance.getItem("${JOBNAME}") 
	def jobFullName = thisJob.getFullName()
	def thisJoblastBuildNumber = thisJob.getNextBuildNumber() - 1
	def thisBuild = thisJob.getBuildByNumber(thisJoblastBuildNumber)
	def thisProject = hudson.model.Hudson.getInstance().getItemByFullName(jobFullName)
	
	def buildDisplayName = BUILD_DISPLAYNAME	
	buildDisplayName = buildDisplayName.trim()
	manager.listener.logger.println ("\nSetting the Build Display name - ${buildDisplayName}")
	println ("\nSetting the Build Display name - ${buildDisplayName}")
	thisBuild.setDisplayName(buildDisplayName)
	
	def buildDescription = "Build ${BUILD_DISPLAYNAME} successfully downloaded."	
	def downStreamJob = thisProject.getDownstreamProjects()
	if (!downStreamJob.empty){
		def downStreamJobName = downStreamJob[0].getName()
		def downStreamJobBuildNumber = downStreamJob[0].getNextBuildNumber()
		buildDescription = buildDescription + "\nDownstream Build - ${downStreamJobName} - Build #${downStreamJobBuildNumber} will be triggered."
	}
	buildDescription = buildDescription.trim()
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
jobLog = getCurrentConsoleLogfromJobName(jobNamePassed)
if (isBuildSuccessfullyDownloaded (jobLog)){
	manager.listener.logger.println("\nBuild Successfully downloaded.")
	println("\nBuild Successfully downloaded.")
	sepBuildNumber = getBuildNumFromBuildLog(jobLog)
	if (sepBuildNumber?.trim()){
		manager.listener.logger.println("\tBuild Number ${sepBuildNumber}.")
		println("\tBuild Number ${sepBuildNumber}.")
		setBuildDisplayNameFromJobName (jobNamePassed,sepBuildNumber)
	}
	else {
		manager.listener.logger.println("\nBuild Number returned is blank or null.")
		println("\nBuild Number returned is blank or null.")
	}
}
else {
	manager.listener.logger.println("\nNo testable Build found")
	println("\nNo testable Build found")
}
return true
