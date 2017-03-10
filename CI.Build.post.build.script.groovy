// This code should be added in the Groovy post build section
// This is generic code that is for the Continues Integration jobs Smoke & Full Run
// This will do the following thing
// 1. Retrieve the Name of the JOB.
// 2. It will update the Description & the Display Name which is passed.
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


def setBuildDisplayfromJobName (JOBNAME,BUILD_DISPLAYNAME){
	// This function will only set the Build Display name.
	def thisJob = hudson.model.Hudson.instance.getItem("${JOBNAME}") 
	def jobFullName = thisJob.getFullName()
	def thisJoblastBuildNumber = thisJob.getNextBuildNumber() - 1
	def thisBuild = thisJob.getBuildByNumber(thisJoblastBuildNumber)
	def thisProject = hudson.model.Hudson.getInstance().getItemByFullName(jobFullName)	
	
	def buildDisplayName = BUILD_DISPLAYNAME
	buildDisplayName = buildDisplayName.trim()
	manager.listener.logger.println ("Setting the Build Display name - ${buildDisplayName}")
	println ("Setting the Build Display name - ${buildDisplayName}")
	thisBuild.setDisplayName(buildDisplayName)
	
	def buildDescription = "Jenkins Build #${thisJoblastBuildNumber} Completed.\nThis ​was ​build ​with ​Changlist ​#${BUILD_DISPLAYNAME}."
	buildDescription = buildDescription.trim()	
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

manager.listener.logger.println ("Passing the following parameters\n\tJob Name : ${jobNamePassed}\n\tSEP Build Short : ${sepChangeList}")
println ("Passing the following parameters\n\tJob Name : ${jobNamePassed}\n\tSEP Build Short : ${sepChangeList}")
setBuildDisplayfromJobName (jobNamePassed , sepChangeList)