package viewController;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.Features2d;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import jade.CommunicationAgent;
import model.Computation;
import model.ImageModel;
import model.ObjectModel;

public class CommunicationController {
	private static CommunicationAgent agent;
	private static int matched_scene;
	private static int matched_obj;
	public static List<KeyPoint> harris_objKeyPoint = new ArrayList<KeyPoint>();
	public static List<KeyPoint> harris_sceneKeyPoint = new ArrayList<KeyPoint>();
	public static List<KeyPoint> canny_objKeyPoint = new ArrayList<KeyPoint>();
	public static List<KeyPoint> canny_sceneKeyPoint = new ArrayList<KeyPoint>();
	public static List<KeyPoint> shitomasi_objKeyPoint = new ArrayList<KeyPoint>();
	public static List<KeyPoint> shitomasi_sceneKeyPoint = new ArrayList<KeyPoint>();

	public static void setMatchedScene(int matched_scene) {
		CommunicationController.matched_scene = matched_scene;
	}

	public static int getMatchedScene() {
		return matched_scene;
	}

	public static void setMatchedObj(int matched_obj) {
		CommunicationController.matched_obj = matched_obj;
	}

	public static int getMatchedObj() {
		return matched_obj;
	}

	/*
	 * Assign the canny agent property to Canny Controller
	 */
	public void setAgent(CommunicationAgent agent) {
		CommunicationController.agent = agent;
	}

	/*
	 * Initialize controller of Canny Agent
	 */
	public void init() {
		agent.setController(this);
	}

	public static int find_path(String[] percentages) {
		int position = 0;
		double recomm = find_highest(percentages);
		String highest = new Double(recomm).toString();

		for (int i = 0; i < 3; i++) {
			if (percentages[i].equals(highest)) {
				position = i;
			}
		}
		return position;
	}

	public static double find_highest(String[] percentages) {
		double value[] = new double[percentages.length];
		double max = 0;
		for (int i = 0; i < 3; i++) {
			value[i] = Double.parseDouble(percentages[i]);
			if (value[i] > max) {
				max = value[i];
			}
		}
		return max;
	}

	public static List<KeyPoint> keypoint_extraction(String abs_path, String hybrid_tag) {
		List<KeyPoint> list = new ArrayList<KeyPoint>();
		File file = new File(abs_path + "(" + hybrid_tag + ")" + ".txt");
		// KeyPoint keypoints = new KeyPoint();
		List<String> lines = null;
		try {
			lines = FileUtils.readLines(file);
			// list.add(keypoints);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		String[] temp;

		for (int i = 0; i < lines.size(); i++) {
			temp = lines.get(i).split("}");
			String point = temp[temp.length - 2];
			String keypoint = point.substring(14);
			String[] coordinates = keypoint.split(",");
			String x = coordinates[coordinates.length - 2];
			String y = coordinates[coordinates.length - 1];
			double x_val = Double.parseDouble(x);
			double y_val = Double.parseDouble(y);
			list.add(new KeyPoint((int) x_val, (int) y_val, -1));
		}
		return list;
	}

	public static void matched_corners(String object, String scene, String hybrid_tag) {

		String bookObject = object;
		String bookScene = scene;

		Mat objectImage = Imgcodecs.imread(bookObject, Imgcodecs.CV_LOAD_IMAGE_COLOR);
		Mat sceneImage = Imgcodecs.imread(bookScene, Imgcodecs.CV_LOAD_IMAGE_COLOR);

		MatOfKeyPoint objectKeyPoints = new MatOfKeyPoint();
		objectKeyPoints.fromList(keypoint_extraction(object, hybrid_tag));

		Computation.setObjectCorners(objectKeyPoints.total());
		MatOfKeyPoint objectDescriptors = new MatOfKeyPoint();
		DescriptorExtractor descriptorExtractor = DescriptorExtractor.create(DescriptorExtractor.ORB);

		descriptorExtractor.compute(objectImage, objectKeyPoints, objectDescriptors);

		// Create the matrix for output image.
		Mat outputImage = new Mat(objectImage.rows(), objectImage.cols(), Imgcodecs.CV_LOAD_IMAGE_COLOR);
		Scalar newKeypointColor = new Scalar(0, 0, 255);

		Features2d.drawKeypoints(objectImage, objectKeyPoints, outputImage, newKeypointColor, 0);

		// Match object image with the scene image
		MatOfKeyPoint sceneKeyPoints = new MatOfKeyPoint();
		sceneKeyPoints.fromList(keypoint_extraction(scene, hybrid_tag));
		MatOfKeyPoint sceneDescriptors = new MatOfKeyPoint();

		Computation.setSceneCorners(sceneKeyPoints.total());

		descriptorExtractor.compute(sceneImage, sceneKeyPoints, sceneDescriptors);

		Mat matchoutput = new Mat(sceneImage.rows() * 2, sceneImage.cols() * 2, Imgcodecs.CV_LOAD_IMAGE_COLOR);
		Scalar matchestColor = new Scalar(0, 0, 255);

		List<MatOfDMatch> matches = new LinkedList<MatOfDMatch>();
		DescriptorMatcher descriptorMatcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);

		descriptorMatcher.knnMatch(objectDescriptors, sceneDescriptors, matches, 2);

		LinkedList<DMatch> goodMatchesList = new LinkedList<DMatch>();

		// float nndrRatio = 0.7f;

		for (int i = 0; i < matches.size(); i++) {
			MatOfDMatch matofDMatch = matches.get(i);
			DMatch[] dmatcharray = matofDMatch.toArray();
			DMatch m1 = dmatcharray[0];
			goodMatchesList.addLast(m1);
		}

		Computation.setMatchedCorners(goodMatchesList.size());
		if (goodMatchesList.size() >= 7) {

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

			Core.perspectiveTransform(obj_corners, scene_corners, homography);

			Mat img = Imgcodecs.imread(bookScene, Imgcodecs.CV_LOAD_IMAGE_COLOR);

			Imgproc.line(img, new Point(scene_corners.get(0, 0)), new Point(scene_corners.get(1, 0)),
					new Scalar(0, 255, 0), 4);
			Imgproc.line(img, new Point(scene_corners.get(1, 0)), new Point(scene_corners.get(2, 0)),
					new Scalar(0, 255, 0), 4);
			Imgproc.line(img, new Point(scene_corners.get(2, 0)), new Point(scene_corners.get(3, 0)),
					new Scalar(0, 255, 0), 4);
			Imgproc.line(img, new Point(scene_corners.get(3, 0)), new Point(scene_corners.get(0, 0)),
					new Scalar(0, 255, 0), 4);

			MatOfDMatch goodMatches = new MatOfDMatch();
			goodMatches.fromList(goodMatchesList);

			Features2d.drawMatches(objectImage, objectKeyPoints, sceneImage, sceneKeyPoints, goodMatches, matchoutput,
					matchestColor, newKeypointColor, new MatOfByte(), 2);

			Imgcodecs.imwrite(ImageModel.get_path() + "\\" + ImageModel.get_file_name() + hybrid_tag + ".jpg",
					outputImage);
			// Imgcodecs.imwrite("images//matchoutput.jpg", matchoutput);
			// Imgcodecs.imwrite("images//img.jpg", img);
		} else {

		}
	}

	public static double computeAccuracy() {

		double scene_corners = Computation.getSceneCorners();
		double object_corners = Computation.getObjectCorners();
		double matched_corners = Computation.getMatchedCorners();
		double accuracy = 0;

		accuracy = ((matched_corners / object_corners) + (matched_corners / scene_corners)) / 2;
		accuracy = accuracy * 100;

		return accuracy;
	}

	public static double compute_hybrid_HSC(List<KeyPoint> sceneKeyPoint1, List<KeyPoint> sceneKeyPoint2,
			List<KeyPoint> sceneKeyPoint3, List<KeyPoint> objKeyPoint1, List<KeyPoint> objKeyPoint2,
			List<KeyPoint> objKeyPoint3, String hybrid_tag) {
		// TODO Auto-generated method stub
		List<KeyPoint> mergeScene = new ArrayList<KeyPoint>();
		mergeScene.addAll(sceneKeyPoint1);
		mergeScene.addAll(sceneKeyPoint2);
		mergeScene.addAll(sceneKeyPoint3);

		List<KeyPoint> mergeObject = new ArrayList<KeyPoint>();
		mergeObject.addAll(objKeyPoint1);
		mergeObject.addAll(objKeyPoint2);
		mergeScene.addAll(sceneKeyPoint3);
		double hybrid_matched = getHybridMatched(mergeScene, mergeObject, hybrid_tag);
		double accuracy = (((hybrid_matched / (double) mergeScene.size())
				+ (hybrid_matched / (double) mergeObject.size())) / 2) * 100;
		return accuracy;
	}

	public static double compute_hybrid(List<KeyPoint> sceneKeyPoint1, List<KeyPoint> sceneKeyPoint2,
			List<KeyPoint> objKeyPoint1, List<KeyPoint> objKeyPoint2, String hybrid_tag) {

		List<KeyPoint> mergeScene = new ArrayList<KeyPoint>();
		mergeScene.addAll(sceneKeyPoint1);
		mergeScene.addAll(sceneKeyPoint2);

		List<KeyPoint> mergeObject = new ArrayList<KeyPoint>();
		mergeObject.addAll(objKeyPoint1);
		mergeObject.addAll(objKeyPoint2);
		double hybrid_matched = getHybridMatched(mergeScene, mergeObject, hybrid_tag);
		double accuracy = (((hybrid_matched / (double) mergeScene.size())
				+ (hybrid_matched / (double) mergeObject.size())) / 2) * 100;
		return accuracy;
	}

	private static double getHybridMatched(List<KeyPoint> matchedScene, List<KeyPoint> matchedObj, String hybrid_tag) {
		// TODO Auto-generated method stub

		String bookScene = ImageModel.get_path() + "/" + ImageModel.get_file_name();
		String bookObject = ObjectModel.get_path() + "/" + ObjectModel.get_file_name();

		Mat objectImage = Imgcodecs.imread(bookObject, Imgcodecs.CV_LOAD_IMAGE_COLOR);
		Mat sceneImage = Imgcodecs.imread(bookScene, Imgcodecs.CV_LOAD_IMAGE_COLOR);

		MatOfKeyPoint objectKeyPoints = new MatOfKeyPoint();
		objectKeyPoints.fromList(matchedObj);

		Computation.setObjectCorners(objectKeyPoints.total());
		MatOfKeyPoint objectDescriptors = new MatOfKeyPoint();
		DescriptorExtractor descriptorExtractor = DescriptorExtractor.create(DescriptorExtractor.ORB);

		descriptorExtractor.compute(objectImage, objectKeyPoints, objectDescriptors);

		// Create the matrix for output image.
		Mat outputImage = new Mat(objectImage.rows(), objectImage.cols(), Imgcodecs.CV_LOAD_IMAGE_COLOR);
		Scalar newKeypointColor = new Scalar(0, 0, 255);

		Features2d.drawKeypoints(objectImage, objectKeyPoints, outputImage, newKeypointColor, 0);

		// Match object image with the scene image
		MatOfKeyPoint sceneKeyPoints = new MatOfKeyPoint();
		sceneKeyPoints.fromList(matchedScene);
		MatOfKeyPoint sceneDescriptors = new MatOfKeyPoint();

		Computation.setSceneCorners(sceneKeyPoints.total());

		descriptorExtractor.compute(sceneImage, sceneKeyPoints, sceneDescriptors);

		Mat matchoutput = new Mat(sceneImage.rows() * 2, sceneImage.cols() * 2, Imgcodecs.CV_LOAD_IMAGE_COLOR);
		Scalar matchestColor = new Scalar(0, 0, 255);

		List<MatOfDMatch> matches = new LinkedList<MatOfDMatch>();
		DescriptorMatcher descriptorMatcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);

		descriptorMatcher.knnMatch(objectDescriptors, sceneDescriptors, matches, 2);

		LinkedList<DMatch> goodMatchesList = new LinkedList<DMatch>();

		// float nndrRatio = 0.7f;

		for (int i = 0; i < matches.size(); i++) {
			MatOfDMatch matofDMatch = matches.get(i);
			DMatch[] dmatcharray = matofDMatch.toArray();
			DMatch m1 = dmatcharray[0];
			goodMatchesList.addLast(m1);
		}

		Computation.setMatchedCorners(goodMatchesList.size());
		if (goodMatchesList.size() >= 7) {

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

			Core.perspectiveTransform(obj_corners, scene_corners, homography);

			Mat img = Imgcodecs.imread(bookScene, Imgcodecs.CV_LOAD_IMAGE_COLOR);

			Imgproc.line(img, new Point(scene_corners.get(0, 0)), new Point(scene_corners.get(1, 0)),
					new Scalar(0, 255, 0), 4);
			Imgproc.line(img, new Point(scene_corners.get(1, 0)), new Point(scene_corners.get(2, 0)),
					new Scalar(0, 255, 0), 4);
			Imgproc.line(img, new Point(scene_corners.get(2, 0)), new Point(scene_corners.get(3, 0)),
					new Scalar(0, 255, 0), 4);
			Imgproc.line(img, new Point(scene_corners.get(3, 0)), new Point(scene_corners.get(0, 0)),
					new Scalar(0, 255, 0), 4);

			MatOfDMatch goodMatches = new MatOfDMatch();
			goodMatches.fromList(goodMatchesList);

			Features2d.drawMatches(objectImage, objectKeyPoints, sceneImage, sceneKeyPoints, goodMatches, matchoutput,
					matchestColor, newKeypointColor, new MatOfByte(), 2);

			Imgcodecs.imwrite(ImageModel.get_path() + "\\" + ImageModel.get_file_name() + hybrid_tag + ".jpg",
					outputImage);
		} else {

		}
		return goodMatchesList.size();
	}

	public static List<KeyPoint> getMatchedScene(List<KeyPoint> canny_sceneKeyPoint,
			List<KeyPoint> harris_sceneKeyPoint, String hybrid_tag) {
		String bookObject = ImageModel.get_path() + "\\" + ImageModel.get_file_name();
		String bookScene = ImageModel.get_path() + "\\" + ImageModel.get_file_name();

		Mat objectImage = Imgcodecs.imread(bookObject, Imgcodecs.CV_LOAD_IMAGE_COLOR);
		Mat sceneImage = Imgcodecs.imread(bookScene, Imgcodecs.CV_LOAD_IMAGE_COLOR);
		MatOfKeyPoint cannyKeyPoints = new MatOfKeyPoint();
		cannyKeyPoints.fromList(canny_sceneKeyPoint);

		MatOfKeyPoint objectDescriptors = new MatOfKeyPoint();
		DescriptorExtractor descriptorExtractor = DescriptorExtractor.create(DescriptorExtractor.ORB);

		descriptorExtractor.compute(objectImage, cannyKeyPoints, objectDescriptors);

		// Create the matrix for output image.
		Mat outputImage = new Mat(objectImage.rows(), objectImage.cols(), Imgcodecs.CV_LOAD_IMAGE_COLOR);
		Scalar newKeypointColor = new Scalar(255, 0, 0);

		Features2d.drawKeypoints(objectImage, cannyKeyPoints, outputImage, newKeypointColor, 0);

		// Match object image with the scene image
		MatOfKeyPoint sceneKeyPoints = new MatOfKeyPoint();
		sceneKeyPoints.fromList(harris_sceneKeyPoint);
		MatOfKeyPoint sceneDescriptors = new MatOfKeyPoint();
		descriptorExtractor.compute(sceneImage, sceneKeyPoints, sceneDescriptors);

		Mat matchoutput = new Mat(sceneImage.rows() * 2, sceneImage.cols() * 2, Imgcodecs.CV_LOAD_IMAGE_COLOR);
		Scalar matchestColor = new Scalar(0, 255, 0);

		List<MatOfDMatch> matches = new LinkedList<MatOfDMatch>();
		DescriptorMatcher descriptorMatcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);

		descriptorMatcher.knnMatch(objectDescriptors, sceneDescriptors, matches, 2);

		LinkedList<DMatch> goodMatchesList = new LinkedList<DMatch>();

		for (int i = 0; i < matches.size(); i++) {
			MatOfDMatch matofDMatch = matches.get(i);
			DMatch[] dmatcharray = matofDMatch.toArray();
			DMatch m1 = dmatcharray[0];
			goodMatchesList.addLast(m1);
		}
		List<KeyPoint> kp = new ArrayList<KeyPoint>();
		if (goodMatchesList.size() >= 7) {

			List<KeyPoint> objKeypointlist = cannyKeyPoints.toList();
			List<KeyPoint> scnKeypointlist = sceneKeyPoints.toList();

			LinkedList<Point> objectPoints = new LinkedList<>();
			LinkedList<Point> scenePoints = new LinkedList<>();

			for (int i = 0; i < goodMatchesList.size(); i++) {
				try {
					kp.add(new KeyPoint((int) scnKeypointlist.get(goodMatchesList.get(i).queryIdx).pt.x,
							(int) scnKeypointlist.get(goodMatchesList.get(i).queryIdx).pt.y, -1));
				} catch (java.lang.ArrayIndexOutOfBoundsException e) {

				}
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

			Core.perspectiveTransform(obj_corners, scene_corners, homography);

			Mat img = Imgcodecs.imread(bookScene, Imgcodecs.CV_LOAD_IMAGE_COLOR);

			Imgproc.line(img, new Point(scene_corners.get(0, 0)), new Point(scene_corners.get(1, 0)),
					new Scalar(0, 255, 0), 4);
			Imgproc.line(img, new Point(scene_corners.get(1, 0)), new Point(scene_corners.get(2, 0)),
					new Scalar(0, 255, 0), 4);
			Imgproc.line(img, new Point(scene_corners.get(2, 0)), new Point(scene_corners.get(3, 0)),
					new Scalar(0, 255, 0), 4);
			Imgproc.line(img, new Point(scene_corners.get(3, 0)), new Point(scene_corners.get(0, 0)),
					new Scalar(0, 255, 0), 4);

			MatOfDMatch goodMatches = new MatOfDMatch();
			goodMatches.fromList(goodMatchesList);

			Features2d.drawMatches(objectImage, cannyKeyPoints, sceneImage, sceneKeyPoints, goodMatches, matchoutput,
					matchestColor, newKeypointColor, new MatOfByte(), 2);

			// Imgcodecs.imwrite(ImageModel.get_path() + "\\" + ImageModel.get_file_name() +
			// hybrid_tag + ".jpg",
			// outputImage);
			// Imgcodecs.imwrite("images//matchoutput.jpg", matchoutput);
			// Imgcodecs.imwrite("images//img.jpg", img);

		} else {

		}
		setMatchedScene(goodMatchesList.size());

		return kp;
	}

	public static double hybrid_accuracy(Double value1, Double value2) {
		// TODO Auto-generated method stub
		double acc = (value1 + value2) / 2;
		return acc;
	}

	public static void setHarrisScenePt(List<KeyPoint> harris_sceneKeyPoint) {
		CommunicationController.harris_sceneKeyPoint = harris_sceneKeyPoint;
	}

	public static List<KeyPoint> getHarrisScenePt() {
		return harris_sceneKeyPoint;
	}

	public static void setCannyScenePt(List<KeyPoint> canny_sceneKeyPoint) {
		CommunicationController.canny_sceneKeyPoint = canny_sceneKeyPoint;
	}

	public static List<KeyPoint> getCannyScenePt() {
		return canny_sceneKeyPoint;
	}

	public static void setShiTomasiScenePt(List<KeyPoint> shitomasi_sceneKeyPoint) {
		CommunicationController.shitomasi_sceneKeyPoint = shitomasi_sceneKeyPoint;
	}

	public static List<KeyPoint> getShiTomasiScenePt() {
		return shitomasi_sceneKeyPoint;
	}

	//
	public static void setHarrisObjPt(List<KeyPoint> harris_objKeyPoint) {
		CommunicationController.harris_objKeyPoint = harris_objKeyPoint;
	}

	public static List<KeyPoint> getHarrisObjPt() {
		return harris_objKeyPoint;
	}

	public static void setCannyObjPt(List<KeyPoint> canny_objKeyPoint) {
		CommunicationController.canny_objKeyPoint = canny_objKeyPoint;
	}

	public static List<KeyPoint> getCannyObjPt() {
		return canny_objKeyPoint;
	}

	public static void setShiTomasiObjPt(List<KeyPoint> shitomasi_objKeyPoint) {
		CommunicationController.shitomasi_objKeyPoint = shitomasi_objKeyPoint;
	}

	public static List<KeyPoint> getShiTomasiObjPt() {
		return shitomasi_objKeyPoint;
	}

	//
}