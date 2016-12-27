unsigned long starttime;
short len = 188;//170;

void setup() {
  // put your setup code here, to run once:
  Serial.begin(9600);
  pinMode(7, INPUT);
}

void loop() {
  // put your main code here, to run repeatedly:
  unsigned int occ;
  starttime = micros();
  short prevState = 0;
  do
  {
    if(digitalRead(7) == 0 && prevState ==1)
      occ ++;

    prevState = digitalRead(7);
  }
  while(micros() - starttime < 2 * pow(10, 6));
  
  Serial.print("Viteza (km/h):");
  Serial.println((len*occ*pow(10,-5)*3600)/((micros() - starttime)*pow(10,-6)), DEC);
}
