import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.*;

public class LosslessCompressor extends JFrame {
    private int height; //the height of the image
    private int width; //the width of the image
    private int[][] red; //red[i][j] is the value of red channel of the pixel[i][j]
    private int[][] green; //green[i][j] is the value of green channel of the pixel[i][j]
    private int[][] blue; //blue[i][j] is the value of blue channel of the pixel[i][j]
    private byte[] header;
    private int codeLength; //# of bytes for a symbol. Either 2 or 3

    private int bytesToInt(byte[] bytes, int offset) {
        return ((int) bytes[offset] & 0xff) << 24 |
                ((int) bytes[offset - 1] & 0xff) << 16 |
                ((int) bytes[offset - 2] & 0xff) << 8 |
                ((int) bytes[offset - 3] & 0xff);
    }

    public void readBMP(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        BufferedInputStream bis = new BufferedInputStream(fis);
        boolean reversed; //whether the image is stored reversely
        long emptyBytes; //the last bytes of a row may be meaningless
        header = new byte[54];

        bis.read(header, 0, 54); //The first 54 bytes are header
        width = bytesToInt(header, 21); //get the width of the image
        height = bytesToInt(header, 25); // get the height of the image
        red = new int[height][width];
        green = new int[height][width];
        blue = new int[height][width];

        //if the first bit of the 25th byte is 0, the image is reversed (bottom-up)
        reversed = ((int) header[25] & 0x80) == 0;

        if (width * 3 % 4 != 0) {
            emptyBytes = 4 - (width * 3 % 4);
        } else {
            emptyBytes = 0;
        }

        if (reversed) { //if the image is reversed, read from bottom to top
            for (int i = height - 1; i >= 0; i--) {
                for (int j = 0; j < width; j++) {
                    blue[i][j] = bis.read();
                    green[i][j] = bis.read();
                    red[i][j] = bis.read();
                }
                if (emptyBytes != 0) { //skip empty bytes
                    bis.skip(emptyBytes);
                }
            }
        } else { //if the image is not reversed, read from top to bottom
            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    blue[i][j] = bis.read();
                    green[i][j] = bis.read();
                    red[i][j] = bis.read();
                }
                if (emptyBytes != 0) {
                    bis.skip(emptyBytes);
                }
            }
        }

        fis.close();
        bis.close();
    }

    private boolean canEncodeWithTwoBytes(int[][] color) {
        HashMap<HashableArray, Integer> dic = new HashMap<>();
        int code = 0;
        for (int i = 0; i <= 255; i++) { //initialize the dictionary
            dic.put(new HashableArray(i), code++);
        }

        HashableArray array = new HashableArray(color[0][0]);

        for (int i = 0; i < color.length; i++) { //LZW Algorithm. Try to encode a symbol with 2 bytes
            for (int j = 0; j < color[0].length; j++) {
                if (i == 0 && j == 0) {
                    continue;
                }

                int c = color[i][j];
                HashableArray temp = array.append(c);

                if (dic.containsKey(temp)) {
                    array.add(c);
                } else {
                    dic.put(temp, code++);
                    if (code == (1 << 16) - 1) { // 2 bytes are deficient to encode a symbol
                        return false;
                    }
                    array = new HashableArray(c);
                }
            }
        }

        return true;
    }

    public void encodeColor(BufferedOutputStream bos, int[][] color) throws IOException {
        HashMap<HashableArray, Integer> dic = new HashMap<>();
        int code = 0;
        for (int i = 0; i <= 255; i++) { //initialize the dictionary for LZW algorithm
            dic.put(new HashableArray(i), code++);
        }

        HashableArray array = new HashableArray(color[0][0]);

        for (int i = 0; i < color.length; i++) { //encode using LZW algorithm
            for (int j = 0; j < color[0].length; j++) {
                if (i == 0 && j == 0) {
                    continue;
                }

                int c = color[i][j];
                HashableArray temp = array.append(c);

                if (dic.containsKey(temp)) {
                    array.add(c);
                } else {
                    if(codeLength == 3) {
                        //if we use 3 bytes to encode a symbol, we will store the 17th bits through the 24th bit
                        //otherwise, we only need to store the 1st bit though the 16th bit
                        bos.write((dic.get(array) >> 16) & 0xFF);
                    }
                    bos.write((dic.get(array) >> 8) & 0xFF);
                    bos.write(dic.get(array) & 0xFF);

                    dic.put(temp, code++);
                    array = new HashableArray(c);
                }
            }
        }

        if(codeLength == 3) {
            //if we use 3 bytes to encode a symbol, we will store the 17th bits through the 24th bit
            //otherwise, we only need to store the 1st bit though the 16th bit
            bos.write((dic.get(array) >> 16) & 0xFF);
        }
        bos.write((dic.get(array) >> 8) & 0xFF);
        bos.write(dic.get(array) & 0xFF);
    }

    public void compress(File file) throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        BufferedOutputStream bos = new BufferedOutputStream(fos);

        bos.write(header);
        bos.flush();

        if(canEncodeWithTwoBytes(red) && canEncodeWithTwoBytes(green) && canEncodeWithTwoBytes(blue)) {
            // if every channel can be encoded with 2 bytes per symbol, then use 2 bytes per symbol
            codeLength = 2;
        } else {
            // otherwise, use 3 bytes per symbol
            codeLength = 3;
        }

        bos.write(codeLength);
        encodeColor(bos, red); //encode the red channel of the image
        encodeColor(bos, green); //encode the green channel of the image
        encodeColor(bos, blue); //encode the blue channel of the image

        bos.flush();
        fos.close();
        bos.close();
    }

    public void showBMP(String title, int x, int y) {
        this.setTitle(title);
        this.setSize(width, height);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        this.setResizable(true);
        this.setVisible(true);
        this.setLocation(x, y);

        DrawPanel drawPanel = new DrawPanel();

        this.add(drawPanel);
    }

    public class DrawPanel extends JPanel {
        public void paint(Graphics g) {
            super.paint(g);
            for (int i = 0; i < height; i++) { // paint every pixel
                for (int j = 0; j < width; j++) {
                    g.setColor(new Color(red[i][j], green[i][j], blue[i][j]));
                    g.fillRect(j, i, 1, 1);
                }
            }
        }
    }

    public static void main(String[] args) {
        try {
            FileSelector fs = new FileSelector();
            String path = fs.getPath();
            if (path != null && path.endsWith(".bmp")) {
                //the name of the compressed IN3 file
                String compressedPath = path.substring(0, path.length() - 3) + "IN3";
                File originalFile = new File(path);
                File compressedFile = new File(compressedPath);

                LosslessCompressor lc = new LosslessCompressor();
                lc.readBMP(originalFile);
                lc.showBMP("Original", 100, 300); //show the original file
                long startTime = System.currentTimeMillis();
                lc.compress(compressedFile); //make the compressed IN3 file.
                long endTime = System.currentTimeMillis();

                long originalSize = originalFile.length();
                long compressedSize = compressedFile.length();

                System.out.println("Original file size: " + originalSize);
                System.out.println("Compressed file size: " + compressedSize);
                System.out.println("Compression ratio: " + originalSize * 1.0 / compressedSize);
                System.out.println("Running time: " + (float)(endTime - startTime) / 1000 + "s");

                //the name of the decompressed file
                String decompressedPath = path.substring(0, path.length() - 4) + "_lossless_decompressed.bmp";
                File decompressedFile = new File(decompressedPath);
                INFileReader fileReader = new INFileReader();
                fileReader.readIN(compressedFile);
                fileReader.decompress(decompressedFile); //decompress the IN3 file

                LosslessCompressor displayer = new LosslessCompressor();
                displayer.readBMP(decompressedFile);
                displayer.showBMP("Compressed", 1000, 300); //show the decompressed file
            } else if(path != null && path.endsWith(".IN3")) { //if the input file is IN3 file, decompress and display
                File compressedFile = new File(path);
                LosslessCompressor lc = new LosslessCompressor();

                //the name of the decompressed file
                String decompressedPath = path.substring(0, path.length() - 4) + "_lossless_decompressed.bmp";
                File decompressedFile = new File(decompressedPath);
                INFileReader fileReader = new INFileReader();
                fileReader.readIN(compressedFile);
                fileReader.decompress(decompressedFile);

                lc.readBMP(decompressedFile);
                lc.showBMP("Compressed", 100, 300); //show the image of the input IN3 file
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class INFileReader {
    private int height; //the height of the image
    private int width; //the width of the image
    private int[][] red; //red[i][j] is the value of red channel of the pixel[i][j]
    private int[][] green; //green[i][j] is the value of green channel of the pixel[i][j]
    private int[][] blue; //blue[i][j] is the value of blue channel of the pixel[i][j]
    private ArrayList<Integer> codeSequence = new ArrayList<>();
    private byte[] header;
    private boolean reversed; //whether the image is bottom-up
    private int emptyBytes; //the last bytes of a row may be meaningless

    private int bytesToInt(byte[] bytes, int offset) {
        return ((int) bytes[offset] & 0xff) << 24 |
                ((int) bytes[offset - 1] & 0xff) << 16 |
                ((int) bytes[offset - 2] & 0xff) << 8 |
                ((int) bytes[offset - 3] & 0xff);
    }

    private void readBuffer(ArrayList<Integer> buffer, int n) {
        // read the buffer to produce the sequence of code. Every n bytes form a symbol.
        // n is either 2 or 3
        if(n == 2) {
            for (int i = 0; i + n - 1 < buffer.size(); i += n) {
                int code = ((buffer.get(i) & 0xFF) << 8) | (buffer.get(i + 1) & 0xFF);
                codeSequence.add(code);
            }
        } else {
            for (int i = 0; i + n - 1 < buffer.size(); i += n) {
                int code = ((buffer.get(i) & 0xFF) << 16) | ((buffer.get(i + 1) & 0xFF) << 8) | (buffer.get(i + 2) & 0xFF);
                codeSequence.add(code);
            }
        }
    }

    public void readIN(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        BufferedInputStream bis = new BufferedInputStream(fis);
        header = new byte[54];
        int codeLength; //# of bytes than encode a symbol

        bis.read(header, 0, 54); //The first 54 bytes are header
        width = bytesToInt(header, 21); //get the width of the image
        height = bytesToInt(header, 25); // get the height of the image

        //if the first bit of the 25th byte is 0, the image is reversed (bottom-up)
        reversed = ((int) header[25] & 0x80) == 0;

        red = new int[height][width];
        green = new int[height][width];
        blue = new int[height][width];

        if(width * 3 % 4 != 0) {
            emptyBytes = 4 - (width * 3 % 4);
        } else {
            emptyBytes = 0;
        }

        codeLength = bis.read(); //read the # of bytes that encode a symbol

        ArrayList<Integer> buffer = new ArrayList<>();
        int temp;
        while((temp = bis.read()) != -1) { // read all data into buffer
            buffer.add(temp);
        }

        readBuffer(buffer, codeLength); //read the buffer

        fis.close();
        bis.close();
    }

    private int decodeColor(int[][] color, int start) {
        HashMap<Integer, HashableArray> dic = new HashMap<>();
        int code = 0;
        for(int i = 0; i <= 255; i++) {
            dic.put(code++, new HashableArray(i));
        }

        int row = 0;
        int column = 0;
        int currentCode, previousCode;

        currentCode = codeSequence.get(start);
        color[row][column++] = dic.get(currentCode).getArray().get(0);
        for(int i = start + 1; i < codeSequence.size(); i++) { //LZW decoder algorithm.
            // The detail of the decoder algorithm can be found in my report
            previousCode = currentCode;
            currentCode = codeSequence.get(i);

            if(dic.containsKey(currentCode)) {
                ArrayList<Integer> tempArray = dic.get(currentCode).getArray();
                for(int item: tempArray) {
                    color[row][column++] = item;
                    if(column == width) { //if a row ends, decode the next row
                        row++;
                        column = 0;
                        if(row == height) { //the whole image is decoded
                            return i + 1;
                        }
                    }
                }
                HashableArray previousArray = dic.get(previousCode);
                int current = dic.get(currentCode).getArray().get(0);
                dic.put(code++, previousArray.append(current));
            } else {
                HashableArray previousArray = dic.get(previousCode);
                int current = dic.get(previousCode).getArray().get(0);
                dic.put(code++, previousArray.append(current));

                ArrayList<Integer> tempArray = previousArray.append(current).getArray();
                for(int item: tempArray) {
                    color[row][column++] = item;
                    if(column == width) { //if a row ends, decode the next row
                        row++;
                        column = 0;
                        if(row == height) { //the whole image is decoded
                            return i + 1;
                        }
                    }
                }
            }
        }

        return -1;
    }

    public void decompress(File file) throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        BufferedOutputStream bos = new BufferedOutputStream(fos);

        bos.write(header);
        byte[] skip = null;
        if(emptyBytes > 0) {
            skip = new byte[emptyBytes];
        }

        int greenStart = decodeColor(red, 0); // decode red channel
        int blueStart = decodeColor(green, greenStart); // decode green channel
        int end = decodeColor(blue, blueStart); // decode blue channel

        if(reversed) { // if the image is stored reversely
            for(int i = height - 1; i >= 0; i--) {
                for(int j = 0; j < width; j++) {
                    bos.write(blue[i][j]);
                    bos.write(green[i][j]);
                    bos.write(red[i][j]);
                }

                if(skip != null) {
                    bos.write(skip); //skip the few bytes at the end of a row
                }
            }
        } else {
            for(int i = 0; i < height; i++) {
                for(int j = 0; j < width; j++) {
                    bos.write(blue[i][j]);
                    bos.write(green[i][j]);
                    bos.write(red[i][j]);
                }

                if(skip != null) {
                    bos.write(skip); //skip the few bytes at the end of a row
                }
            }
        }

        bos.flush();

        fos.close();
        bos.close();
    }
}
