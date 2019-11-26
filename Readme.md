## Code usage instructions

### The test equipment is Samsung Nexus S

### 1.The function of  the **master** branch is to test the time it takes for a UDP packet to be sent .

### 2.The  function of  the **dev1**  branch is to use th e physical method to switch wifi for  experimentation.

### 3. The function of  the **dev2**  branch is to use the analog method of  changing the network segment to switch wifi for experiment.



### The **wpa_supplicant.conf**  file in /data/misc/wifi/

Content in the file

```bash
ctrl_interface=wlan0
disable_scan_offload=1
update_config=0
device_name=soju
manufacturer=Samsung
model_name=Nexus S
model_number=Nexus S
serial_number=38347D2AF65E00EC
device_type=10-0050F204-5
config_methods=physical_display virtual_push_button
p2p_disabled=1
p2p_no_group_iface=1

network={
        ssid="asd"
        key_mgmt=NONE
        mode=1
        frequency=2412
}
```

### time synchronize
Before the experiment, you need to synchronize the device time. This software can synchronize thime.
[ClockSync](https://github.com/greatgeek/Collaborative-discovery/releases)



