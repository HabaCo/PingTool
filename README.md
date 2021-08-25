# PingTool
A simple ping tool with customizable arguments

### Simple Build
 - with default options
    
   `Ping.Builder()`
 - with more options
   
   `Ping.Builder().addOption(Ping.OptionTimeout.apply { value = 5 })`
 - build
   
   `Ping.Builder().build()`
 - clear options
   
   `Ping.Builder().build().clearOption()`

### Build-in options
 - Ping.OptionInterval -- interval of ping
 - Ping.OptionCount -- count(s) of ping
 - Ping.OptionTimeout -- timeout of ping per count
 - Ping.OptionPackages -- packages of ping

### Default options
 - Ping.OptionTimeout = 1
 - Ping.OptionCount = 1

### Samples
    // build pingTool with additional argument
    val pingTool = Ping.Builder()
            .addOption(Ping.OptionTimeout.apply { value = 5 })  // timeout 5 seconds
            .build()

    // chek pinging busy state
    pingTool.isBusy

    // set target host
    pingTool.destination = "127.0.0.1"

    // run asynchronized with arguments from options builder
    pingTool.runSync().also { pingResult ->
        // do stuff with pingResult
    }
    
    // or run asynchronized with custom arguments
    pingTool.runSync(" -i 200 ").also { pingResult ->
            // do stuff with pingResult
        }
