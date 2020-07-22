# addon-app-template
RDP addon app template
______________________________________________
Set environment variables
RDP_URL=manage.rdpengg14.riversand-dataplatform.com;RDP_PORT=8085;REQUEST_FILE_NAME=request.json;TENANT=autoappdev;REQUEST_FILE_PATH=source/java/;SOURCE_CONFIG_PATH=batch/job_defs/helloworld/resource_files/;POD_ID=rdpengg14

Replace 'helloworld' with the application name
____________________________________________________________

In batch/job_defs/helloworld replace 'helloworld'  with the application name
In application.json replace 'helloworld'  with the application name
In batch/job_defs/helloworld/job_def.json replace 'helloworld' with job defination id
_____________________________________________________________
Build Java project
 Root pom location is at source/java
_________________________________________________
Publish to azure batch
BATCH_STORAGE_KEY="XXXXXX"  ./${APPS_SDK_DIR}/code/batch/deployment/application/publish.sh rsbatch-east-test
Replace 'rsbatch-east-test' with resource group name



 



