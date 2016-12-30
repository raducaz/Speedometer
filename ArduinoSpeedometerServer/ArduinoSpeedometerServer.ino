#include <adk.h>
#include <usbhub.h>
#include "Thread.h"
#include "ThreadController.h"
#include <TimerOne.h>

// Satisfy IDE, which only needs to see the include statment in the ino.
#ifdef dobogusinclude
#include <spi4teensy3.h>
#include <SPI.h>
#endif

class SensorThread: public Thread
{
public:
  unsigned long tripStartTime = 0;
  unsigned long sampleStartTime = 0;
  unsigned long rotations;
  unsigned long duration; // in ms
  unsigned long totalRotations;
  unsigned long totalDuration; // in ms
  boolean prevState = 1; // no magnet present

  boolean isRunning = false;
  boolean isSampling = false;
  int pin;

  void startSample()
  {
    unsigned long waitStart = micros();
    while(isRunning && (micros() - waitStart)< 1000) {}; // wait until isNotRunning for max 1 ms
    
    isSampling = true;
    sampleStartTime = micros();
    rotations = 0;
  }
  void endSample()
  {
    isSampling = false;
    duration = (micros() - sampleStartTime) / pow(10,3); // in ms
  }

  // Function executed on thred execution
  void run(){
    isRunning = true;

    if(digitalRead(pin) == 0 && prevState == 1) //magnet present after absense of it
    {
      if(isSampling) // count rotation only when sampling
        rotations ++;
      
      if(tripStartTime == 0) // initialize the starting time for the trip when first rotation is made
        tripStartTime = micros();

      // Keep this in pair
      totalRotations ++;
      totalDuration = (micros() - tripStartTime) / pow(10, 3);
    }

    prevState = digitalRead(pin);
    runned();
    
    isRunning = false;
  }
};

SensorThread digital7 = SensorThread();
ThreadController controller = ThreadController();

USB Usb;
ADK adk(&Usb, "Radu Cazacu SRL",
              "USB Host Shield",
              "Arduino Terminal for Android",
              "2.0",
              "http://www.google.com",
              "0000000000000001");

void setup()
{
  //Serial.begin(115200);
//#if !defined(__MIPSEL__)
//  while (!Serial); // Wait for serial port to connect - used on Leonardo, Teensy and other boards with built-in USB CDC serial connection
//#endif
//  Serial.println("\r\nBegin initial setup");

  if (Usb.Init() == -1) {
  //    Serial.println("OSCOKIRQ failed to assert");
      while (1); //halt
    }

  initSensorThread();

}
void timerCallback(){
  controller.run();
}
void initSensorThread()
{
  Timer1.stop();

  digital7 = SensorThread();
  // Magnetic sensor pin
  digital7.pin = 7;
  digital7.totalRotations = 0;
  digital7.totalDuration = 0;
  digital7.setInterval(1); // in ms

  controller.add(&digital7);

  Timer1.initialize(500); // in micro second (us)
  Timer1.attachInterrupt(timerCallback);
  Timer1.start();
}
void loop()
{
  Usb.Task();
  if ( adk.isReady() == false ) {
//    Serial.println("USB not ready");
  }
  else
  {
    char* rcvMsg = readUsb();
    
    if(strcmp(rcvMsg, "RESETRESET") == 0) //TODO: check the source of this duplication at receive
    {
      //Serial.println("Reseting");
      
      initSensorThread();
      return;
    }
    
    digital7.startSample();
    delay(1000); // reasonalbe delay > 1s for geting rotations sample  
    digital7.endSample();  
  
    /* Send the data - this delays even more */
    String sRotations = String(digital7.rotations);
    String sDuration = String(digital7.duration);
    String sTotalRotations = String(digital7.totalRotations);
    String sTotalDuration = String(digital7.totalDuration);
  
    String msg = sRotations + ";" + sDuration + ";" + sTotalRotations + ";" + sTotalDuration;
    uint8_t data[msg.length()+1];
    msg.toCharArray(data,msg.length()+1);
    uint8_t dataLen = sizeof(data);
  
//    Serial.print("Send ");Serial.print(dataLen);Serial.println(" chars");
    uint8_t rcode = adk.SndData( sizeof(data), data);
//    Serial.println(rcode);
  }
}
char* readUsb()
{
  uint8_t rcode;
  uint16_t len = 64;
  uint8_t msg[len] = { 0x00 };

  Usb.Task();
  if ( adk.isReady() == false ) {
    return 10;
  }
  
  rcode = adk.RcvData(&len, msg);
//  Serial.print("Receive rcode:");
//  Serial.println(rcode, HEX);
  if ( rcode & ( rcode != hrNAK )) {
      // Handle error  
  }
  if (len > 0) {
//    Serial.print("Len:");
//    Serial.println(len);

    char data[len];
    for ( uint8_t i = 0; i < len; i++ ) {
      data[i] = (char)msg[i];
    }

    return data;
  }

  return msg;
}


