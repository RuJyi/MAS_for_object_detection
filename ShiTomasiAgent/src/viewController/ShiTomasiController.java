package viewController;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.Core.MinMaxLocResult;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.Features2d;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import jade.ShiTomasiAgent;
import javafx.fxml.FXML;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import model.ImageModel;

public class ShiTomasiController {
	@FXML
	public ImageView ori_img;
	@FXML
	public ImageView processed_img;
	final FileChooser fileChooser = new FileChooser();

	private static ShiTomasiAgent agent;

	/*
	 * Assign the Shi-Tomasi agent property to Shi-Tomasi Controller
	 */
	public void setAgent(ShiTomasiAgent agent) {
		ShiTomasiController.agent = agent;
	}

	/*
	 * Initialize controller of Shi-Tomasi Agent
	 */
	public void init() {
		agent.setController(this);
	}

	/*
	 * Shi-Tomasi Corner Detector algorithm
	 */
	public List<KeyPoint> doShiTomasi(String path, String name) throws IOException {
		Mat image = Imgcodecs.imread(path + "/" + name);
		Mat sceneGrayScale = new Mat();
		double qualityLevel = 0.1;
		double minDistance = 10;
		int blockSize = 9;
		boolean useHarrisDetector = true;
		double k = 0.1;
		int maxCorners = 100;
		Scalar circleColor = new Scalar(0, 0, 255, 0);
		Imgproc.cvtColor(image, sceneGrayScale, Imgproc.COLOR_BGR2GRAY);

		MatOfPoint corners = new MatOfPoint();

		// Detect corners by using Shi-Tomasi library function
		Imgproc.goodFeaturesToTrack(sceneGrayScale, corners, maxCorners, qualityLevel, minDistance, new Mat(),
				blockSize, useHarrisDetector, k);

		Point[] cornerpoints = corners.toArray();
		List<KeyPoint> KeyPointList = new ArrayList<KeyPoint>();

		for (int i = 0; i < cornerpoints.length; i++) {
			Imgproc.circle(image, new Point((int) cornerpoints[i].x, (int) cornerpoints[i].y), 5, circleColor, 2, 8, 0);
			KeyPointList.add(new KeyPoint((int) cornerpoints[i].x, (int) cornerpoints[i].y, -1));
		}
		return KeyPointList;
	}

	public static void matched_corners(String object, Mat scene, List<KeyPoint> keyPointList) {
		String bookObject = object;
		System.out.println(bookObject);

		System.out.println("Started....");
		System.out.println("Loading images...");
		Mat objectImage = Imgcodecs.imread(bookObject, Imgcodecs.CV_LOAD_IMAGE_COLOR);
		Mat sceneImage = scene;

		MatOfKeyPoint objectKeyPoints = new MatOfKeyPoint();
		objectKeyPoints.fromList(keyPointList);
		System.out.println("Detecting key points...");
		System.out.println("Total number of corners in object: " + objectKeyPoints.total());

		MatOfKeyPoint objectDescriptors = new MatOfKeyPoint();
		DescriptorExtractor descriptorExtractor = DescriptorExtractor.create(DescriptorExtractor.ORB);
		System.out.println("Computing descriptors...");
		descriptorExtractor.compute(objectImage, objectKeyPoints, objectDescriptors);

		// Create the matrix for output image.
		Mat outputImage = new Mat(objectImage.rows(), objectImage.cols(), Imgcodecs.CV_LOAD_IMAGE_COLOR);
		Scalar newKeypointColor = new Scalar(255, 0, 0);

		System.out.println("Drawing key points on object image...");
		Features2d.drawKeypoints(objectImage, objectKeyPoints, outputImage, newKeypointColor, 0);

		// Match object image with the scene image
		MatOfKeyPoint sceneKeyPoints = new MatOfKeyPoint();
		sceneKeyPoints.fromList(keyPointList);
		MatOfKeyPoint sceneDescriptors = new MatOfKeyPoint();
		System.out.println("Detecting key points in background image...");
		System.out.println("number of key points in scene:" + sceneKeyPoints.total());
		System.out.println("Computing descriptors in background image...");
		descriptorExtractor.compute(sceneImage, sceneKeyPoints, sceneDescriptors);

		Mat matchoutput = new Mat(sceneImage.rows() * 2, sceneImage.cols() * 2, Imgcodecs.CV_LOAD_IMAGE_COLOR);
		Scalar matchestColor = new Scalar(0, 255, 0);

		List<MatOfDMatch> matches = new LinkedList<MatOfDMatch>();
		DescriptorMatcher descriptorMatcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
		System.out.println("Matching object and scene images...");
		descriptorMatcher.knnMatch(objectDescriptors, sceneDescriptors, matches, 2);

		System.out.println("Calculating good match list...");
		LinkedList<DMatch> goodMatchesList = new LinkedList<DMatch>();

		// float nndrRatio = 0.7f;

		for (int i = 0; i < matches.size(); i++) {
			MatOfDMatch matofDMatch = matches.get(i);
			DMatch[] dmatcharray = matofDMatch.toArray();
			DMatch m1 = dmatcharray[0];
			goodMatchesList.addLast(m1);
		}
		System.out.println("Number of matched corners: " + goodMatchesList.size());
		if (goodMatchesList.size() >= 7) {
			System.out.println("Object Found!!!");

			List<KeyPoint> objKeypointlist = objectKeyPoints.toList();
			List<KeyPoint> scnKeypointlist = sceneKeyPoints.toList();

			LinkedList<Point> objectPoints = new LinkedList<>();
			LinkedList<Point> scenePoints = new LinkedList<>();
			for (int i = 0; i < goodMatchesList.size(); i++) {
				objectPoints.addLast(objKeypointlist.get(goodMatchesList.get(i).queryIdx).pt);
				scenePoints.addLast(scnKeypointlist.get(goodMatchesList.get(i).trainIdx).pt);
			}

			MatOfPoint2f objMatOfPoint2f = new MatOfPoint2f();
			objMatOfPoint2f.fromList(objectPoints);
			MatOfPoint2f scnMatOfPoint2f = new MatOfPoint2f();
			scnMatOfPoint2f.fromList(scenePoints);

			Mat homography = Calib3d.findHomography(objMatOfPoint2f, scnMatOfPoint2f, Calib3d.RANSAC, 3);

			Mat obj_corners = new Mat(4, 1, CvType.CV_32FC2);
			Mat scene_corners = new Mat(4, 1, CvType.CV_32FC2);

			obj_corners.put(0, 0, new double[] { 0, 0 });
			obj_corners.put(1, 0, new double[] { objectImage.cols(), 0 });
			obj_corners.put(2, 0, new double[] { objectImage.cols(), objectImage.rows() });
			obj_corners.put(3, 0, new double[] { 0, objectImage.rows() });

			System.out.println("Transforming object corners to scene corners...");
			Core.perspectiveTransform(obj_corners, scene_corners, homography);

			Mat img = scene;

			Imgproc.line(img, new Point(scene_corners.get(0, 0)), new Point(scene_corners.get(1, 0)),
					new Scalar(0, 255, 0), 4);
			Imgproc.line(img, new Point(scene_corners.get(1, 0)), new Point(scene_corners.get(2, 0)),
					new Scalar(0, 255, 0), 4);
			Imgproc.line(img, new Point(scene_corners.get(2, 0)), new Point(scene_corners.get(3, 0)),
					new Scalar(0, 255, 0), 4);
			Imgproc.line(img, new Point(scene_corners.get(3, 0)), new Point(scene_corners.get(0, 0)),
					new Scalar(0, 255, 0), 4);

			System.out.println("Drawing matches image...");
			MatOfDMatch goodMatches = new MatOfDMatch();
			goodMatches.fromList(goodMatchesList);

			Features2d.drawMatches(objectImage, objectKeyPoints, sceneImage, sceneKeyPoints, goodMatches, matchoutput,
					matchestColor, newKeypointColor, new MatOfByte(), 2);

			Imgcodecs.imwrite(ImageModel.get_file_name() + ".jpg", outputImage);
		} else {
			System.out.println("Object Not Found");
		}

		System.out.println("Ended....");

	}

	public static String img_matching(Mat img, String obj, int match_method) {
		// String percentage = null;
		Mat templ = Imgcodecs.imread(obj);
		// / Create the result matrix
		int result_cols = img.cols() - templ.cols() + 1;
		int result_rows = img.rows() - templ.rows() + 1;
		Mat result = new Mat(result_rows, result_cols, CvType.CV_32FC1);

		// Do the Matching and Normalize
		Imgproc.matchTemplate(img, templ, result, match_method);

		// Localizing the best match with minMaxLoc
		MinMaxLocResult mmr = Core.minMaxLoc(result);

		Point matchLoc;
		double maxVal;

		matchLoc = mmr.maxLoc;
		maxVal = mmr.maxVal * 100;
		DecimalFormat df = new DecimalFormat("#.00");
		String percentage = df.format(maxVal);
		Imgproc.rectangle(img, matchLoc, new Point(matchLoc.x + templ.cols(), matchLoc.y + templ.rows()),
				new Scalar(0, 0, 255), 2);

		return percentage;
	}
}
