bardplugins
===========

The BARD plugin architecture is based on Java servlets. See the [plugin specification](https://github.com/ncatsdpiprobedev/bard/wiki/Plugins) for more details on how to write plugins. Thus each plugin is bundled as a WAR file and then deployed into a BARD application server. This repository hosts some example BARD plugins that can be used as a template for new plugins. To build the WAR files for all plugins do
```
ant all
```
To generate WAR files for individual plugins just specify the plugin name. For example,
```
ant ssearch
``` 
The WAR files are located under the ```deploy/``` directory for each plugin and can simply be copied into the ```webapps/``` directory of your app container.
