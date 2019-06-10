gitBranch = gitBranchname
giturl = gitURL
version = version
buildType = buildType
emailNotify = emailNotify

node{
    buildSteps()
}

def buildSteps(){
    echo'Deployment job started'
    satge('Checkout Project'){
        checkoutProject()
    }
    satge('Release and Tag'){
        if(buildType == 'RELEASE'){
            echo"Building release version"
            releaseAndTag()
        }else{
            echo"Building sanpshot version"
            buildSnapshot()
        }
    }

    stage('Run Acceptance Tests'){
        echo'running stage acceptance steaps'
    }
    stage('deployment'){
        //deployment method call
    }
    satge('Completing Job')
            {
                setBuildDescription()
                //trigEmail
            }
}

def checkoutProject(){
    cleanWorkspace()
    checkoutFromGit()
}

def checkoutFromGit(){
    checkout(
            [
              $class: 'GitSCM'
                    branches:[[name:'*/'+gitBranch]],
                    doGenerateSubmoduleConfigurations:fasle,
                    extensions[
                            [$class: 'RelativeTargetDirectory', relativeTargetDir:'checkout'],
                            [$class: 'LocalBranch',localBranch: gitBranch],
                            [$class: 'WipeWorkspace'],
                            [$class: 'CleanCheckout'],
                            [$class: 'CleanBeforeCheckout'] ],
                    gitTool: 'jgit',
                    submoduleCFG:[],
                    userRemoteConfigs:[[
                            credentialsId:'value'
                            name:'origin'
                            url: gitURL
                    ]]
            ]
    )
}

def buildSnapshot(){
    dir('checkout'){
        try{
            sh 'mvn clean install'
        } catch(e)
        echo e.message
        trigEmail("Failed- Build sanpshot")
        failBuildErrors()
        }finally{
        publishReport('target/site/scoverage', 'index.html','SCoverage Report')
    }
    }
}

def cleanWorkspace(){
    sh 'rm -rf checkout'
}

def failBuildErrors(){
    sh 'exit 1'
}
//below methods doesnot have complete implement code
def setBuildDescription(){
}
def publishReport(reportDirPath, reportFileName, reportName){
}


def trigEmail(buildStatus){
    if (emailNotify != ""){
        emailext body:'Please visit'+ env.Build_URL+'to know more about build status', subject: "Job '$env.JOB_NAME' ($env.BUILD_NUMBER)" + buildStatus, to: eamilNotify
    }else{
        echo "Not able to trigger email becasue no receiver email was listed"
    }
}