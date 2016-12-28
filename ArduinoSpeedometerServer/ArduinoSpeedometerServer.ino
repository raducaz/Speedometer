#include <adk.h>
#include <usbhub.h>

// Satisfy IDE, which only needs to see the include statment in the ino.
#ifdef dobogusinclude
#include <spi4teensy3.h>
#include <SPI.h>
#endif

USB Usb;
ADK adk(&Usb, "Radu Cazacu SRL",
              "USB Host Shield",
              "Arduino Terminal for Android",
              "2.0",
              "http://www.google.com",
              "0000000000000001");

unsigned long duration; // duration for the rotations in ms
unsigned long rotations;
unsigned long totalDuration; 
unsigned long totalRotations;
short len = 170; // rotating made by magnet circle circumference in mm

void setup()
{
  Serial.begin(115200);
#if !defined(__MIPSEL__)
  while (!Serial); // Wait for serial port to connect - used on Leonardo, Teensy and other boards with built-in USB CDC serial connection
#endif
  Serial.println("\r\nADK demo start");

  if (Usb.Init() == -1) {
    Serial.println("OSCOKIRQ failed to assert");
    while (1); //halt
  }

  // Magnetic sensor pin
  pinMode(7, INPUT);
}
char* readUsb()
{
  uint8_t rcode;
  uint16_t len = 64;
  uint8_t msg[len] = { 0x00 };
  
  Usb.Task();
  if ( adk.isReady() == false ) {
    return;
  }
  
  rcode = adk.RcvData(&len, msg);
  Serial.print("Receive rcode:");
  Serial.println(rcode, HEX);
  if ( rcode & ( rcode != hrNAK )) {
    
  }
  if (len > 0) {
    Serial.print("Len:");
    Serial.println(len);

    char data[len];
    for ( uint8_t i = 0; i < len; i++ ) {
      data[i] = (char)msg[i];
    }

    return data;
  }

  return msg;
}
uint8_t writeUsb(uint8_t* data)
{
  uint8_t rcode;
  
  Usb.Task();
  if ( adk.isReady() == false ) {
    return;
  }
  
  Serial.print("Sending:");
  Serial.println((char*)data);
  rcode = adk.SndData( strlen((char*)data), data);
  Serial.print("Send rcode:");
  Serial.println(rcode, HEX);
  
  if (rcode & (rcode != hrNAK)) {
    Serial.print(F("\r\nError data sent: "));
    Serial.println(rcode, HEX);
  }

  return rcode;
}

void loop()
{
  readSpeedSensor(2 * pow(10, 6)); // scan for 2 seconds (in micro seconds)
}

void readSpeedSensor(short scanInterval)
{
  starttime = micros();
  boolean prevState = 0;
  rotations = 0;
  duration = 0;
  do
  {
    if(digitalRead(7) == 1 && prevState == 0)
      rotations ++;

    prevState = digitalRead(7);
  }
  while(micros() - starttime < scanInterval);
  
  duration = (micros() - starttime)*pow(10, 3); // duration in ms
}


