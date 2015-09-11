import java.awt.AWTEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.io.FilenameUtils;

import com.aleadam.AllProfilesData;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.OvalRoi;
import ij.io.FileSaver;
import ij.io.SaveDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

public class MultipleProfilePeaks implements ExtendedPlugInFilter,
		DialogListener {
	private static final String[] REGION_TYPES = new String[]{"Circle", "Square (faster)", "No filter"};
	private static final int FLAGS = NO_UNDO | NO_CHANGES | DOES_8G | DOES_16
			| DOES_32 | KEEP_PREVIEW;
	
	private int peakWidth = 20;
	private double stringency = 3;
	private int threshold = 0;
	private int density = 10;
	private int region = 5;
	private int regionType = 2;
	private String name;
	private ImagePlus origImp, workingImp, resultImp;
	private int maxInt;
	private boolean needSave;
	private Float[] pixels;

	public int setup(String arg, ImagePlus imp) {
		if (arg.equals("about")) {
			showAbout();
			return DONE;
		}
		origImp = imp;
		workingImp = imp.duplicate();
		maxInt = (int) workingImp.getProcessor().getMax();
		name = origImp.getTitle();
		return FLAGS;
	}

	public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
		GenericDialog gd = new GenericDialog("Peak detection settings:");
		gd.addSlider("Peak width (2-100)", 2, 100, 20);
		gd.addSlider("Stringency (0.05-4.99)", 0.05, 4.99, 1.0);
		gd.addSlider("Threshold (0-" + maxInt + ")", 0, maxInt, maxInt / 20);
		gd.addSlider("Density requirement (1-50)", 1, 50, 1);
		gd.addSlider("region size (1-20)", 1, 20, 5);
		gd.addChoice("Region type:", REGION_TYPES, REGION_TYPES[2]);
		gd.addPreviewCheckbox(pfr);
		gd.addDialogListener(this);
		gd.showDialog();
		if (gd.wasCanceled())
			return DONE;
		needSave = gd.wasOKed();
		return FLAGS;
	}

	public boolean dialogItemChanged(GenericDialog gd, AWTEvent ev) {
		if (resultImp != null && resultImp.getProcessor() == null)
			return false;

		peakWidth = (int) gd.getNextNumber();
		stringency = gd.getNextNumber();
		threshold = (int) gd.getNextNumber();
		density = (int) gd.getNextNumber();
		region = (int) gd.getNextNumber();
		regionType = gd.getNextChoiceIndex();

		if (peakWidth < 2)
			peakWidth = 2;
		if (peakWidth > 100)
			peakWidth = 100;
		if (stringency < 0.05D)
			stringency = 0.05D;
		if (stringency > 4.99D)
			stringency = 4.99D;
		if (threshold < 0)
			threshold = 0;
		if (threshold > maxInt)
			threshold = maxInt;
		if (region < 1)
			region = 1;
		if (region > 20)
			region = 20;
		if (density < 1)
			density = 1;
		if (density > 50 || density > region * region)
			density = Math.min(50, region * region);

		return true;
	}

	public void setNPasses(int arg0) {
	}

	public void run(ImageProcessor origIP) {

		AllProfilesData pd = new AllProfilesData(workingImp, name, peakWidth,
				stringency, threshold);
		ImageProcessor ip = pd.process();
		if (regionType == 0) {
			ip = densityFilterCircle(ip);
		} else if (regionType == 1) {
			ip = densityFilterSquare(ip);
		}

		if (resultImp == null) {
			ImageStack stack = new ImageStack(ip.getWidth(), ip.getHeight());
			stack.addSlice("1", ip);
			resultImp = new ImagePlus(name + " edges", stack);
			resultImp.show();
		} else {
			float[] orig = (float[]) ip.getPixels();
			float[] dest = (float[]) resultImp.getProcessor().getPixels();
			for (int i = 0; i < orig.length; i++) {
				dest[i] = orig[i];
			}
			resultImp.updateAndDraw();
		}

		if (needSave)
			save(pd);
	}

	private ImageProcessor densityFilterSquare(ImageProcessor ip) {
		ImageProcessor ip2 = new FloatProcessor(ip.getWidth(), ip.getHeight());

		if (density == 1)
			return ip;

		for (int i = region; i < ip.getWidth() - region; i++) {
			for (int j = region; j < ip.getHeight() - region; j++) {
				int count = 0;
				for (int k = -region; k < region; k++) {
					for (int l = -region; l < region; l++) {
						if (ip.getPixel(i + k, j + l) > 0)
							count++;
					}
				}
				if (count >= density)
					ip2.putPixel(i, j, ip.getPixel(i, j));
			}
		}
		return ip2;
	}

	private ImageProcessor densityFilterCircle(ImageProcessor ip) {
		ImageProcessor tmpIP = ip.duplicate();
		ImageProcessor ip2 = new FloatProcessor(ip.getWidth(), ip.getHeight());
		int radius = region;
		OvalRoi roi = new OvalRoi(0, 0, 2 * radius, 2 * radius);
		tmpIP.setRoi(roi);

		if (density == 1)
			return ip;

		for (int i = 0; i < ip.getWidth() - 2 * radius; i++) {
			for (int j = 0; j < ip.getHeight() - 2 * radius; j++) {
				roi.setLocation(i, j);
				ImageProcessor mask = tmpIP.getMask();
				int count = 0;
				for (int k = 0; k < 2 * radius; k++) {
					for (int l = 0; l < 2 * radius; l++) {
						if (mask.getPixel(k, l) > 0
								&& ip.getPixel(i + k, j + l) > 0) {
							count++;
						}
					}
				}
				if (count >= density) {
					ip2.putPixel(i + radius, j + radius,
							ip.getPixel(i + radius, j + radius));
				}
			}
		}
		return ip2;
	}

	private void pruneImage() {
		float[] imagePixels = (float[]) resultImp.getProcessor().getPixels();
		ArrayList<Float> pixArray = new ArrayList<Float>();
		for (int i = 0; i < imagePixels.length; i++) {
			if (imagePixels[i] > 0) {
				pixArray.add(imagePixels[i]);
			}
		}
		pixels = pixArray.toArray(new Float[pixArray.size()]);
	}

	public float getPeakAverage() {
		float peakAverage = 0;
		for (int i = 0; i < pixels.length; i++) {
			peakAverage += pixels[i];
		}
		return peakAverage /= pixels.length;
	}

	public int getPeakCount() {
		return pixels.length;
	}

	public float getPeakMedian() {
		Float[] sortedPixels = pixels.clone();
		Arrays.sort(sortedPixels);
		if (sortedPixels.length % 2 == 0) {
			return (sortedPixels[(sortedPixels.length / 2) - 1] + sortedPixels[sortedPixels.length / 2]) / 2;
		} else {
			return sortedPixels[sortedPixels.length / 2];
		}
	}

	private float getVariance() {
		float var = 0;
		float peakAverage = getPeakAverage();
		for (int i = 0; i < pixels.length; i++) {
			var += (pixels[i] - peakAverage) * (pixels[i] - peakAverage);
		}
		return var / (pixels.length - 1);
	}

	public double getStDev() {
		return Math.sqrt(getVariance());
	}

	public float getMaxPeak() {
		float max = 0;
		for (int i = 0; i < pixels.length; i++) {
			max = max > pixels[i] ? max : pixels[i];
		}
		return max;
	}

	public float getMinPeak() {
		float min = Float.MAX_VALUE;
		for (int i = 0; i < pixels.length; i++) {
			min = min < pixels[i] ? min : pixels[i];
		}
		return min;
	}

	public void save(AllProfilesData pd) {
		try {
			SaveDialog sdImg = new SaveDialog("Save peak image as...", "",
					FilenameUtils.getBaseName(name), ".tif");
			if (sdImg.getDirectory() != null) {
				String saveNameImg = sdImg.getDirectory() + sdImg.getFileName();
				File fileImg = new File(saveNameImg);
				if (!fileImg.exists()) {
					fileImg.createNewFile();
				}
				FileSaver fs = new FileSaver(resultImp);
				fs.saveAsTiff(saveNameImg);
			}

			SaveDialog sdData = new SaveDialog("Save data as...", "",
					FilenameUtils.getBaseName(name), ".csv");
			if (sdData.getDirectory() != null) {
				String saveNameData = sdData.getDirectory()
						+ sdData.getFileName();
				File fileData = new File(saveNameData);
				if (!fileData.exists()) {
					fileData.createNewFile();
				}

				BufferedWriter writer;
				FileWriter fw = new FileWriter(fileData.getAbsoluteFile());
				writer = new BufferedWriter(fw);

				pruneImage();

				writer.write("******************************************");
				writer.newLine();
				writer.write("PEAK STATISTICS:");
				writer.newLine();
				writer.newLine();
				writer.write("Average peak intensity: ," + getPeakAverage());
				writer.newLine();
				writer.write("Peak Standard deviation: ," + getStDev());
				writer.newLine();
				writer.write("Total number of peaks: ," + getPeakCount());
				writer.newLine();
				writer.write("Peak median: ," + getPeakMedian());
				writer.newLine();
				writer.write("Peak range: ," + getMinPeak() + " - "
						+ pd.getMaxPeak());
				writer.newLine();
				writer.newLine();

				writer.write("******************************************");
				writer.newLine();
				writer.write("SETTINGS:");
				writer.newLine();
				writer.newLine();
				writer.write("Peak width setting: ," + peakWidth);
				writer.newLine();
				writer.write("Stringency: ," + stringency);
				writer.newLine();
				writer.write("Threshold: ," + threshold);
				writer.newLine();
				writer.write("Density filter used: ," + REGION_TYPES[regionType]);
				writer.newLine();
				writer.write("Minimum density: ," + density);
				writer.newLine();
				writer.write("Region size: ," + region);
				writer.newLine();
				writer.write("******************************************");

				writer.flush();
				writer.close();
			}
		} catch (IOException e) {
			IJ.showMessage("Error saving file!",
					"Please check that the file is not in use and try running the plugin again.");
		}
	}

	void showAbout() {
		IJ.showMessage(
				"About MultipleProfilePeaks...",
				"It takes a single image and scans each horizontal and vertical line\n"
						+ "to measure the intensity profile in each, finding the peak "
						+ "location and maxima, creating a new image and providing\n"
						+ "data statistics in a csv file for image comparisons.");
	}
}
