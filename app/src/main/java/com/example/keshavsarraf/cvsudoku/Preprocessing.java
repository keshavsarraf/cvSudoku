package com.example.keshavsarraf.cvsudoku;

import android.util.Log;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by keshav.sarraf on 23/12/17.
 */

public class Preprocessing {

    private int smallBoxSize = 28;
    private List<int[]> digitPixelList = new ArrayList<>();


    public List<int[]> getDigitPixelList() {
        return digitPixelList;
    }

    public Mat preprocess(Mat inputImage) {

        Mat processedImage = new Mat();

        Imgproc.GaussianBlur(inputImage, processedImage, new Size(3, 3), 2);
        Imgproc.adaptiveThreshold(processedImage, processedImage, 255, 1, 1, 11, 2);

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();

        Imgproc.findContours(processedImage, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        double area = 0;
        int indexOfContour = 0;

        for (int i=0 ; i < contours.size(); i++) {
            MatOfPoint contour = contours.get(i);
            double contourArea = Imgproc.contourArea(contour);

            area = Math.max(contourArea, area);
            indexOfContour = contourArea == area ? i : indexOfContour;
        }

        Imgproc.drawContours(inputImage, contours, indexOfContour, new Scalar(255, 0, 0), 8);


        Point[] points = getCWCornersFromContour(contours.get(indexOfContour));

//        Imgproc.circle(inputImage, points[0], 20, new Scalar(255, 0, 0));
//        Imgproc.circle(inputImage, points[1], 20, new Scalar(255, 0, 0));
//        Imgproc.circle(inputImage, points[2], 20, new Scalar(255, 0, 0));
//        Imgproc.circle(inputImage, points[3], 20, new Scalar(255, 0, 0));

//        return inputImage;

        MatOfPoint2f src = new MatOfPoint2f(
                points[0],
                points[1],
                points[2],
                points[3]);

        MatOfPoint2f dst = new MatOfPoint2f(
                new Point(0, 0),
                new Point(smallBoxSize*9-1,0),
                new Point(smallBoxSize*9-1,smallBoxSize*9-1),
                new Point(0,smallBoxSize*9-1)

        );

        Mat warpMat = Imgproc.getPerspectiveTransform(src,dst);
        //This is you new image as Mat
        Mat destImage = new Mat();
        Imgproc.warpPerspective(inputImage, destImage, warpMat, inputImage.size());


        //get full sudoku crop from wrarped image
        Mat zeroPadOutput = Mat.zeros(destImage.size(), destImage.type());
        Rect sudokuCrop = new Rect(0, 0, smallBoxSize*9, smallBoxSize*9);
        Mat detectedSudoku = new Mat(destImage, sudokuCrop);

        //copy the sudoku to blank display image
        detectedSudoku.copyTo(zeroPadOutput.rowRange(0, smallBoxSize*9).colRange(0, smallBoxSize*9));

        //fetch the (1,0) cell of sudoku
        int cellXPos = 7;
        int cellYPos = 2;
        Rect smallBoxCrop = new Rect(smallBoxSize*cellXPos, smallBoxSize*cellYPos, smallBoxSize, smallBoxSize);
        Mat detectedSmallBox = new Mat(destImage, smallBoxCrop);

        Imgproc.adaptiveThreshold(detectedSmallBox, detectedSmallBox, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 11, 2);

        int smallBoxStartPosX = smallBoxSize*4;
        int smallBoxStartPosY = smallBoxSize*9  + 100;

        //copy to display image
        detectedSmallBox.copyTo(zeroPadOutput.rowRange(smallBoxStartPosX, smallBoxStartPosX + smallBoxSize).colRange(smallBoxStartPosY, smallBoxStartPosY + smallBoxSize));

        int[] digitPixels = new int[smallBoxSize*smallBoxSize];
        detectedSmallBox.convertTo(detectedSmallBox, CvType.CV_32S);
        detectedSmallBox.get(0, 0, digitPixels);
        digitPixelList.add(digitPixels);


//        Log.i("sudoku-clip", String.valueOf(detectedSudoku.size()));

//        List<List<Double>> innerBoxes = new ArrayList<>();

//        for (int i=0 ; i<smallBoxSize*9; i+=smallBoxSize) {
//            for (int j=0; j<smallBoxSize*9; j+=smallBoxSize) {
//                List<Double> box = new ArrayList<>();
//
//                for (int inner_i=0 ; inner_i<smallBoxSize; inner_i++){
//                    for (int inner_j=0; inner_j<smallBoxSize; inner_j++) {
//                        box.add(destImage.get(j+inner_j,i+inner_i)[0]);
//                    }
//                }
//                innerBoxes.add(box);
//            }
//        }


//        List<Integer> sizes = new ArrayList<>();
//        for (List<Double> innerBox : innerBoxes) {
//            sizes.add(innerBox.size());
//        }
//        Log.i("InnerBoxes", String.valueOf(innerBoxes.get(1)));

        return zeroPadOutput;
    }

    //return clockwise corners from contour starting from topLeft
    //TODO: fix this
    private Point[] getCWCornersFromContour(MatOfPoint contour) {
        Point[] out = new Point[4];

        List<Point> points = contour.toList();
        List<Double> sumXY = points.stream().map(point -> point.x + point.y).collect(Collectors.toList());
        List<Double> diffXY = points.stream().map(point -> point.x - point.y).collect(Collectors.toList());

        Point tl = points.get(0), tr = points.get(0), bl = points.get(0), br = points.get(0);

        double maxSum = sumXY.get(0);
        int maxSumIdx = 0;
        double minSum = sumXY.get(0);
        int minSumIdx = 0;
        double maxDiff = diffXY.get(0);
        int maxDiffIdx = 0;
        double minDiff = diffXY.get(0);
        int minDiffIdx = 0;

        for (int i=0; i< sumXY.size(); i++) {
            maxSum = Math.max(sumXY.get(i), maxSum);
            maxSumIdx = maxSum == sumXY.get(i) ? i : maxSumIdx;

            minSum = Math.min(sumXY.get(i), minSum);
            minSumIdx = minSum == sumXY.get(i) ? i : minSumIdx;

            maxDiff = Math.max(diffXY.get(i), maxDiff);
            maxDiffIdx = maxDiff == diffXY.get(i) ? i : maxDiffIdx;

            minDiff = Math.min(diffXY.get(i), minDiff);
            minDiffIdx = minDiff == diffXY.get(i) ? i : minDiffIdx;
        }


        out[0] = points.get(minSumIdx);
        out[1] = points.get(maxDiffIdx);
        out[2] = points.get(maxSumIdx);
        out[3] = points.get(minDiffIdx);


        Log.i("Corners", "tl : " + tl + " || br : " + br + " || max : " + maxSum);

        return out;
    }


}
