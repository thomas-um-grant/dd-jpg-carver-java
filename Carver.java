//Binary carver for jpegs
//Proof of concept for 3600 lab assignment
//1-17-19
//Edited 2-9-20
//-Dr. G
//Edited 2/28/20
// Thomas G - Kurtis L

import java.io.*;
import java.util.Scanner;
import java.util.concurrent.Semaphore;
 
public class Carver
{
  public static void main(String[] args){

    Scanner scn = new Scanner(System.in);
    //Asking for inputStream
    System.out.print("Welcome,\nPlease input your file name, including its type: ");
    String fileName = scn.next();
    //Asking where to save outputStream
    System.out.println("\nThanks, now how would you like to call the result folder?\n");
    String resultFolder = scn.next();

    //Need to create a folder here
    //Creating a File object
    File file = new File(resultFolder);
    //Creating the directory
    boolean bool = file.mkdir();
    if(bool){
       System.out.println("Directory created successfully");
    }else{
       System.out.println("Sorry couldn’t create specified directory");
    }

    CarveThem.carve(fileName, resultFolder);

  }
}

class CarveThem
{
  static String path;
  static String format = ".jpg";
  static long totalBytesInput;

  public static void carve(String fileName, String resultFolder){

    path = resultFolder+"/Goblin";

    String [] outputFiles = new String[40];
    for(int i=0; i<outputFiles.length; i++)
    {
      outputFiles[i] = path+i+format;
    }

    //Creating 4 different inputStream, one for each thread
    try 
    {
      InputStream bufferedPrep = new FileInputStream(fileName);
      InputStream inputStream = new BufferedInputStream(bufferedPrep);
      InputStream inputStream2 = new BufferedInputStream(bufferedPrep);
      InputStream inputStream3 = new BufferedInputStream(bufferedPrep);
      InputStream inputStream4 = new BufferedInputStream(bufferedPrep);
  
      totalBytesInput = totalBytes(fileName);

      //Creating new instances of carving
      Carving [] carvings = new Carving[4];
      
      carvings[0] = new Carving(inputStream, 0);
      carvings[1] = new Carving(inputStream2, (int)totalBytesInput/4);
      carvings[2] = new Carving(inputStream3, (int)totalBytesInput/2);
      carvings[3] = new Carving(inputStream4, (int)totalBytesInput*3/4);
      
      //Creating the 4 threads to run carving
      int threads = 4;

      Thread [] t = new Thread[threads];

      for (int x = 0; x<threads; x++)
      {
        t[x] = new Thread(carvings[x]);
        t[x].start();
      }
    }
    catch (IOException exIO){
      System.out.print(exIO);
    }
  }

  //Get the path of the outputStream
  public static String getPath(){
    return path;
  }

  //Get the format of the outputStream (jpeg)
  public static String getFormat(){
    return format;
  }

  //Get the total number of bytes in inputStream
  public static long getTotalBytes(){
    return totalBytesInput;
  }

  //Count total number of bytes in inputStream
  public static long totalBytes(String fileName){
    long counter = 0;
    try{
      InputStream countStream = new FileInputStream(fileName);
      while(countStream.read() != -1){
        counter++;
      }
    }
    catch (IOException ex){
      ex.printStackTrace();
    }
    return counter;
  }

}

class Carving implements Runnable
{
  private InputStream inputStream;
  private int startByte; //Where to start looking in the inputStream
  private OutputStream outputStream;
  static int countOutputFile = 0; //byte counter
  static Semaphore mutex = new Semaphore(1); //mutex
  private long tooEarly = 0; //Check if an end has been found too early
  private long deadEnd = 0; //Check if an end hasn't been found after a while
  private long stopByte; //Tells the thread when to stop
  static boolean trash = false; //Delete file

  public Carving(InputStream inputStream, int startByte){
    this.inputStream = inputStream;
    this.startByte = startByte;
  }

  public void run(){

    try{
  
      //input stream returns bytes in the form of integer values
      int byteRead;
      int byte2; 
      int byte3; 

      //Jpegs start with ff d8 ff (255 216 255) and end with ff d9 (255 217)
      //If a jpeg has extended file header information (EXIF) it will have two ff d8 ff e#'s

      stopByte = 0;

      while((byteRead = inputStream.read()) != -1) {

        if(byteRead == 255)//Start of header
          {
            inputStream.mark(4);//mark the current position
             
            //read in the next 3 bytes for header check
            byte2 = inputStream.read();
            byte3 = inputStream.read();
             
            //if next 2 bytes are a match call carving method
            if(byte2 == 216 && byte3==255)
            { 
              //Mutex here
              mutex.acquire();
              //Update outputStream
              outputStream = new FileOutputStream(CarveThem.getPath()+countOutputFile+CarveThem.getFormat());
              //carve
              carveJpeg(inputStream, outputStream);
              //Delete if trash
              if(trash){
                File file = new File(CarveThem.getPath()+countOutputFile+CarveThem.getFormat());
                file.delete();
              }
              //Reset trash
              trash = false;
              //Increment outputstream to use here
              countOutputFile ++;
              //end Mutex here
              mutex.release();
            }
            else
            {
              inputStream.reset(); //if it isn't a match reset to mark
            }
          }

        //We stop this thread once it reaches a little more than a quarter of the total number of bytes in the file
        stopByte++;
        if((int)stopByte > ((int)CarveThem.getTotalBytes()/4)+50){
          break;
        }
      }
    }catch(IOException ex) {
      ex.printStackTrace();
    }
    catch(InterruptedException e){}
  }



  public void carveJpeg(InputStream inputStream, OutputStream outputStream)
  {
    int byteRead;
             
    try 
    {
      //write the header
      outputStream.write(255);
      outputStream.write(216);
      outputStream.write(255);
      
      //reset tooEarly and deadEnd
      tooEarly = 0;
      deadEnd = 0;

      //write loop until you find the footer ff d9 -> 255 217
      while((byteRead = inputStream.read()) != -1) 
      {
      
        //Increment too early
        tooEarly++;

        //If number of bytes > 600000, it's a dead end so we delete the file
        deadEnd++;
        if(deadEnd > 600000){
          trash = true;
          break;
        }
        
        outputStream.write(byteRead);
        //if you find an ff look for a d9
        if(byteRead == 255)
        {
          byteRead = inputStream.read();
          outputStream.write(byteRead);
          //if you find a d9, then stop
          if((byteRead == 217) && !(tooEarly < 3000)) //If tooEarly < 4000, then it's a fake end and we ignore it
          {
            break; //this is the end
          }
        }
      }//end while
    }//end try
    catch (IOException ex) {
        ex.printStackTrace();
    }
  }
}

