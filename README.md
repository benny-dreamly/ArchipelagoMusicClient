# Music Player for Archipelago

This is a music player app that allows you to automate the process of sending checks in an Archipelago music Manual. 
It was originally built to work exclusively with my Taylor Swift music manual, but I have since tweaked it to work mostly generically with other music manuals. 
You might have to do some work upfront to get your music manual to work properly with this, but other than that it should be mostly plug and play. 

## How does this work?

This makes use of the Archipelago Java Client library in order to communicate with the Archipelago server. 
Configuration is simple enough, but I will elaborate more on the schema of the required configuration files,
and the structure of the configuration folder in the `docs` folder of this repository. This will include every file you need and examples.

You do need to have the songs locally on your computer, most likely ripped from a CD or obtained from iTunes or a similar online music marketplace.
Streaming will not work with this client. I might consider implementing it in the future, but as of right now,
any music streaming services such as Spotify and Apple Music will not work with this client. 

## How can I get started using the client?

This client makes use of JavaFX, which you will need to make sure you have installed alongside your JDK.

Before running or building the client, make sure you have the following installed:

- **Java Development Kit (JDK) 24 or newer**  
  Make sure your environment variables (`JAVA_HOME`, `PATH`) are configured correctly.

- **JavaFX SDK (matching your JDK version)**  
  JavaFX is required for the UI to function properly.  
  You can download it from [https://openjfx.io](https://openjfx.io).

- **Maven or Gradle (optional, for dependency management)**  
  If you’re building from source before a release build is available, you’ll need a way to download dependencies automatically.

    - Maven: `mvn clean install`
    - Gradle: `gradle build`

    ## How the heck do I configure this?

    Well, you're in luck. If you go to the docs/ folder, you should be able to find a schema and detailed documentation about where the configuration folders are.