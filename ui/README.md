
# Plugin Development Guide

## **Programming skills required**

The developer shall have basic knowledge on:

-   Object Oriented JavaScript (OOJS)
    
-   ES6 classes and language understanding including async/await and promises
    
-   Basics of web components specifications and life cycle
    
-   Basics of HTML5 and CSS3
    
-   Ability to design and code programming flows
    
-   Polymer 3 basics (if writing a complex plugin with advanced rendering)
    
-   Basics of git and [github.com](http://github.com)
    

## **Infrastructure**

Below software are required to be installed in a dev environment to code, compile and create deployment artifact for a plugin.

The developer is also required to provide mentioned Riversand config to connect, deploy and run her under-development plugin component

-   VS Code - latest version
    
-   Node JS with NPM - Version: 10.18.1

-   Chrome (latest) - _add a link here_
    
-   VS Code extensions - eslint - _add a link here_
    
-   VS Code extensions - jsdoc - _add a link here_
    
-   Riversand Config Information:
    
    -   Riversand Developer Sandbox Environment URL (Example: [https://xxyyzzz.riversand.com](https://xxyyzz.riversand.com) or https://devtest.riversand.com)
        
    -   Client Id (Example: AAXX-xwsdf-w42343 or ABCCorp)
        
    -   Client Secret Key (Example: 11ED7069-EE5C-479C-B137-1B0F260E6C30)
        

## Dev Environment Setup

Here are high-level steps to be followed by plugin developers to set up a dev environment. Below activities can be performed using visual studio IDE and integrated terminal on IDE.

1.  Install visual studio code and required extensions
    
2.  Install and setup local git
    
3.  Install npm by following [https://www.npmjs.com/get-npm](https://www.npmjs.com/get-npm)
    
    1.  If you already have npm, it may not be latest version. To update npm, run two commands: npm install -g n and then sudo n latest
        
4. Decide / register unique identifier key for this plugin package. Examples can be: ui-plugin-context-graph-view, ui-plugin-address-verifier.
    
    a. Make sure to define unique key for the package. Eventually this would be generated / aquired by plugin registration processed.

5.  Clone Riversand github repository ui-plugin-template and create new plugin repository with name {{PLUGIN_PACKAGE_NAME}}
    
    a. Create branch of {{PLUGIN_PACKAGE_NAME}} repository in local system
        
    b. If not ready to create git repo. for the plugin, one can just download the ui-plugin-template repo. in local to start with.
        
6.  Open plugin folder on VS Code
    
7.  Find and replace existing `ui-plugin-template` package name in this repository to your registered {{PLUGIN_PACKAGE_NAME}}
    
8.  Find and replace {{PLUGIN_DESC}} token with small description of plugin functionality
    
9.  Execute `npm install` at your plugin folder
    
10.  Update Env Settings file at /config/default.json:
    
    a. serverUrl - dev url of tenant you want to deploy and test plugin
        
    b. Headers - update user id, tenant id, auth client id, auth client secret and user roles. (Out of the box config is preset to engg-az-dev2 environment with [rdwadmin@riversand.com](mailto:rdwadmin@riversand.com) user's secret key)
        
    c. Config looks like below:
        
    d.  ```
        {
            "envSettings": {
                "pluginUniqueIdentifer": "{{PLUGIN_PACKAGE_NAME}}",
                "serverUrl": "https://rdwengg-az-dev2.riversand.com",
                "headers": {
                    "x-rdp-userid": "rdwadmin@riversand.com",
                    "x-rdp-tenantId": "rdwengg-az-dev2",
                    "auth-client-id": "lryFBhv1R9KA3HMJZHPCdThtFODah1M1",
                    "auth-client-secret": "Z6u26qromsQZsfjGC_7Ue5WaeN8driXf96_dCzNIGSA0B8NuuUoGKcqU8zmXbCkb",
                    "x-rdp-clientid": "rdpClient",
                    "x-rdp-userRoles": "\"admin\""
                }
            }   
        }
        ```
        
11.  Push plugin repo to git (add detail)
    
12.  Plugin solution is now ready to code
    

## Plugin Solution

_Add solution structure and more detail here_

Plugin solution comes out of the box with the below modules integrated to get started with coding plugin business logic.

**Riversand specific modules (aka riversand libraries aka rs-lib):**

-   ui-platform-utils - add link here
    
-   ui-platform-aci - add link here
    
-   ui-platform-styles - add link here
    
-   ui-platform-elements - add link here
    
-   ui-platform-dataaccess - add link here
    

**External modules:**

-   Polymer 3
    
-   Babel
    
-   ESLint
    
-   Gulp
    

## Develop plugin (Code, Compile, Unit Test, Deploy and Dev Test)

1.  Create plugin element or plugin function following guide at: dev guide link (TBD: R2)
    
2.  Create base config for plugin element under `/src/plugin-config`, if any by following guide at: plugin config guide link
    
3.  Add docking point config under `/src/docking-host-config` following guide at: docking-host-config guide link
   
    1. Check that the plugin path has been set as below inside docking-host-config.
        
        `/plugin-src/{{PLUGIN_PACKAGE_NAME}}/elements/plugin-hello-world/plugin-hello-world.js` 
    
4.  Ensure plugin logic is testable
    
5.  Lint plugin logic by running `npm run lint` (TBD: R2)
    
6.  Write and run unit test by running `npm run test` for plugin (TBD: R2)
    
7.  Template comes out of the box with `plugin-hello-world`. Template also provides docking host config to deploy plugin as widget on home screen.
    
8.  Dev test:
    
    1.  Run `npm run compile-and-deploy` command to create artifact and deploy plugin into configured dev env.
        
        a. Run `npm run compile-and-deploy -- --DEPLOY_CONFIG` to deploy config changes as needed
            
    2.  Open browser in dev mode(using query string **dev=true**), login and navigate to app where plugin is docked(through docking-host-config)
        
    3.  If app is already loaded, just refresh the page(f5) to verify recent plugin logic changes
        
    4.  Ensure query string dev=true is present all the time
        
    5.  Press _**F12**_ to open dev tools _**to debug**_ plugin logic by navigating to the plugin source file (Provide detail link here)
        
9.  Once the plugin is working as expected, _**push the changes to git**_
    

## Push Changes to Plugin Repository

1.  Once changes are verified, keep pushing changes to plugin git repo
    

## Production deployment

1.  Once plugin is ready with all testing done, submit the plugin to Riversand App Management team following process at : provide guide link here (TBD: R2)
