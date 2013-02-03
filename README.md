bardplugins
===========

The BARD plugin architecture is based on Java servlets. Thus each plugin is bundled as a WAR file and then deployed into a BARD application server. This repository hosts some example BARD plugins that can be used as a template for new plugins. To build the WAR files for all plugins do
```
ant all
```
To generate WAR files for individual plugins just specify the plugin name. For example,
```
ant ssearch
``` 
