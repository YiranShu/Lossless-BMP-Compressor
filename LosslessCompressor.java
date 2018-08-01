import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.*;

public class LosslessCompressor extends JFrame {
    private int height;
    private int width;
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
        boolean reversed;
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
        } else {
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

    public void compress(File file) throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        BufferedOutputStream bos = new BufferedOutputStream(fos);

        bos.write(header);
        bos.flush();

        encodeColor(bos, red);
        encodeColor(bos, green);
        encodeColor(bos, blue);

        bos.flush();
        fos.close();
        bos.close();
    }

    public void encodeColor(BufferedOutputStream bos, int[][] color) throws IOException {
        HashMap<HashableArray, Integer> dic = new HashMap<>();
        int code = 0;
        for (int i = 0; i <= 255; i++) {
            dic.put(new HashableArray(i), code++);
        }

        HashableArray array = new HashableArray(color[0][0]);

        for (int i = 0; i < color.length; i++) {
            for (int j = 0; j < color[0].length; j++) {
                if (i == 0 && j == 0) {
                    continue;
                }

                int c = color[i][j];
                HashableArray temp = array.append(c);

                if (dic.containsKey(temp)) {
                    array.add(c);
                } else {
                    bos.write((dic.get(array) >> 16) & 0xFF);
                    bos.write((dic.get(array) >> 8) & 0xFF);
                    bos.write(dic.get(array) & 0xFF);

                    dic.put(temp, code++);
                    if (code == (1 << 24) - 1) {
                        System.out.println("Warning: code length deficient!");
                    }
                    array = new HashableArray(c);
                }
            }
        }

        bos.write((dic.get(array) >> 16) & 0xFF);
        bos.write((dic.get(array) >> 8) & 0xFF);
        bos.write(dic.get(array) & 0xFF);
        System.out.println("Code: " + (code - Integer.MIN_VALUE));
    }

    public void showBMP(String title) {
        this.setTitle(title);
        this.setSize(width, height);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setLocationRelativeTo(null);

        this.setResizable(true);
        this.setVisible(true);

        DrawPanel drawPanel = new DrawPanel();

        this.add(drawPanel);
    }

    public class DrawPanel extends JPanel {
        public void paint(Graphics g) {
            super.paint(g);
            for (int i = 0; i < height; i++) {
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
                String compressedPath = path.substring(0, path.length() - 3) + "IN3";

                File originalFile = new File(path);
                File compressedFile = new File(compressedPath);

                LosslessCompressor lc = new LosslessCompressor();
                lc.readBMP(originalFile);
                lc.showBMP("Original");
                lc.compress(compressedFile);

                long originalSize = originalFile.length();
                long compressedSize = compressedFile.length();

                System.out.println("Original file size: " + originalSize);
                System.out.println("Compressed file size: " + compressedSize);
                System.out.println("Compression ratio: " + originalSize * 1.0 / compressedSize);

                String decompressedPath = path.substring(0, path.length() - 4) + "_lossless_decompressed.bmp";
                File decompressedFile = new File(decompressedPath);
                INFileReader fileReader = new INFileReader();
                fileReader.readIN(compressedFile);
                fileReader.decompress(decompressedFile);

                lc.readBMP(decompressedFile);
                lc.showBMP("Compressed");
            } else if(path != null && path.endsWith(".IN3")) {
                File compressedFile = new File(path);
                LosslessCompressor lc = new LosslessCompressor();

                String decompressedPath = path.substring(0, path.length() - 4) + "_lossless_decompressed.bmp";
                File decompressedFile = new File(decompressedPath);
                INFileReader fileReader = new INFileReader();
                fileReader.readIN(compressedFile);
                fileReader.decompress(decompressedFile);

                lc.readBMP(decompressedFile);
                lc.showBMP("Compressed");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class INFileReader {
    private int height;
    private int width;
    private int[][] red; //red[i][j] is the value of red channel of the pixel[i][j]
    private int[][] green;
    private int[][] blue;
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

    public void readIN(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        BufferedInputStream bis = new BufferedInputStream(fis);
        header = new byte[54];

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

        ArrayList<Integer> buffer = new ArrayList<>();
        int temp;
        while((temp = bis.read()) != -1) {
            buffer.add(temp);
        }

        for(int i = 0; i + 2 < buffer.size(); i += 3) {
            int code = ((buffer.get(i) & 0xFF) << 16) | ((buffer.get(i + 1) & 0xFF) << 8) | (buffer.get(i + 2) & 0xFF);
            codeSequence.add(code);
        }

        System.out.println("length: " + codeSequence.size());

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
        for(int i = start + 1; i < codeSequence.size(); i++) {
            previousCode = currentCode;
            currentCode = codeSequence.get(i);

            if(dic.containsKey(currentCode)) {
                ArrayList<Integer> tempArray = dic.get(currentCode).getArray();
                for(int item: tempArray) {
                    color[row][column++] = item;
                    if(column == width) {
                        row++;
                        column = 0;
                        if(row == height) {
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
                    if(column == width) {
                        row++;
                        column = 0;
                        if(row == height) {
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

        int greenStart = decodeColor(red, 0);
        int blueStart = decodeColor(green, greenStart);
        int end = decodeColor(blue, blueStart);

        if(reversed) {
            for(int i = height - 1; i >= 0; i--) {
                for(int j = 0; j < width; j++) {
                    bos.write(blue[i][j]);
                    bos.write(green[i][j]);
                    bos.write(red[i][j]);
                }

                if(skip != null) {
                    bos.write(skip);
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
                    bos.write(skip);
                }
            }
        }

        bos.flush();

        fos.close();
        bos.close();
    }
}
