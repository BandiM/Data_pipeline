# Data_pipeline

Built Streaming data pipeline using kafka and spark structured streaming.  FileReader class reads data from two different file formats one is psv and json files , Apply transformations and filters on dataset and resulting dataset persists to postgres DB.

Below are the steps :

•	clone the repo/project

•   Start zookeeper server using below command

     .\bin\windows\zookeeper-server-start.bat .\config\zookeeper.properties 

• Start kafka server 

   .\bin\windows\kafka-server-start.bat .\config\server.properties 

•	intsall postgres DB into your loacl system

•	Re-Build the project 

•	Execute FileReadertest and FileWritertest classes 
