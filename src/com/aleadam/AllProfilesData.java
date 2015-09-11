package com.aleadam;

import java.util.Arrays;

import ij.ImagePlus;
import ij.gui.Line;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

public class AllProfilesData {
	public ImagePlus imp;
	private String name;
	public int width, height;
	private int[] tenths;
	private float peakAverage;
	private int count;
	public int[][] allPixelValues;
	public int[][] peakVal;
	public int[][] peakPos;
	public Line[] lines;
	private int peakWidth;
	private float stringency;

	public AllProfilesData(ImagePlus imp, String name, int peakWidth,
			float stringency, float thresholdInt) {
		this.name = name;
		this.peakWidth = peakWidth;
		this.stringency = stringency;
		width = imp.getWidth();
		height = imp.getHeight();
		lines = generateRoi();

		allPixelValues = new int[lines.length][];
		for (int i = 0; i < lines.length; i++) {
			imp.setRoi(lines[i]);
			double val[] = lines[i].getPixels();
			allPixelValues[i] = new int[val.length];
			for (int j=0; j<val.length; j++) {
				allPixelValues[i][j] = (int) Math.max(0, val[j]-thresholdInt);
			}
		}

		tenths = getTenths(allPixelValues);
	}

	public ImageProcessor process() {
		populatePeaks(allPixelValues, peakWidth, stringency);
		ImageProcessor ip = new ShortProcessor(width, height);
		for (int i = 0; i < height; i++) {
			for (int j = 0; j < peakPos[i].length; j++) {
				ip.putPixelValue(peakPos[i][j], i, peakVal[i][j]);
			}
		}
		for (int i = height; i < height + width; i++) {
			for (int j = 0; j < peakPos[i].length; j++) {
				ip.putPixelValue(i, peakPos[i][j], peakVal[i][j]);
			}
		}
		return ip;
	}

	private Line[] generateRoi() {
		Line[] lines = new Line[height + width];
		for (int i = 0; i < height; i++) {
			lines[i] = new Line(0, i, width, i);
		}
		for (int i = height; i < height + width; i++) {
			lines[i] = new Line(i, 0, i, height);
		}
		return lines;
	}

	private void populatePeaks(int[][] values, int peakWidth,
			float stringency) {
		PeakDetector[] pd = new PeakDetector[values.length];
		peakPos = new int[values.length][];
		peakVal = new int[values.length][];
		count = 0;
		for (int i = 0; i < values.length; i++) {
			pd[i] = new PeakDetector(values[i]);
			peakPos[i] = pd[i].process(peakWidth, stringency);
			peakVal[i] = new int[peakPos[i].length];

			for (int j = 0; j < peakPos[i].length; j++) {
				peakVal[i][j] = values[i][peakPos[i][j]];
				count++;
			}
		}
	}

	public float getPeakAverage() {
		if (peakVal == null)
			return 0;
		for (int i = 0; i < peakVal.length; i++) {
			for (int j = 0; j < peakVal[i].length; j++) {
				peakAverage += peakVal[i][j];
			}
		}
		return peakAverage /= count;
	}

	public int getPeakCount() {
		return count;
	}

	public float getPeakMedian() {
		int size = 0;
		for (int i = 0; i < peakVal.length; i++) {
			size += peakVal[i].length;
		}
		float[] data = new float[size];
		int c = 0;
		for (int i = 0; i < peakVal.length; i++) {
			for (int j = 0; j < peakVal[i].length; j++) {
				data[c++] = peakVal[i][j];
			}
		}
		Arrays.sort(data);
		if (data.length % 2 == 0) {
			return (float) ((data[(data.length / 2) - 1] + data[data.length / 2]) / 2.0);
		} else {
			return data[data.length / 2];
		}
	}

	private float getVariance() {
		if (peakAverage==0) return 0;
		
		float var = 0;
		for (int i = 0; i < peakVal.length; i++) {
			for (int j = 0; j < peakVal[i].length; j++) {
				var += (peakVal[i][j] - peakAverage)
						* (peakVal[i][j] - peakAverage);
			}
		}
		return var / (count - 1);
	}

	public float getStDev() {
		if (count < 2)
			return 0;
		return (float) Math.sqrt(getVariance());
	}

	public int getMaxPeak() {
		int max = 0;
		for (int i = 0; i < peakVal.length; i++) {
			for (int j = 0; j < peakVal[i].length; j++) {
				max = max > peakVal[i][j] ? max : peakVal[i][j];
			}
		}
		return max;
	}

	public int getMinPeak() {
		int min = Integer.MAX_VALUE;
		for (int i = 0; i < peakVal.length; i++) {
			for (int j = 0; j < peakVal[i].length; j++) {
				min = min < peakVal[i][j] ? min : peakVal[i][j];
			}
		}
		return min;
	}

	public String getName() {
		return name;
	}

	public int getTenth(int tenth) {
		return tenths[tenth];
	}

	public float getAverageBelow(int tenth) {
		int size = 0;
		float avg = 0;
		int count = 0;
		for (int i = 0; i < allPixelValues.length; i++) {
			size += allPixelValues[i].length;
		}
		float[] data = new float[size];
		int c = 0;
		for (int i = 0; i < allPixelValues.length; i++) {
			for (int j = 0; j < allPixelValues[i].length; j++) {
				data[c++] = allPixelValues[i][j];
			}
		}
		Arrays.sort(data);
		for (int i = 0; i < (data.length * tenth / 10); i++) {
			avg += data[i];
			count++;
		}
		if (count == 0)
			return 0;
		return avg / count;
	}

	private int[] getTenths(int[][] values) {
		int size = 0;
		int[] tenths = new int[10];
		for (int i = 0; i < values.length; i++) {
			size += values[i].length;
		}
		int[] data = new int[size];
		int c = 0;
		for (int i = 0; i < values.length; i++) {
			for (int j = 0; j < values[i].length; j++) {
				data[c++] = values[i][j];
			}
		}
		Arrays.sort(data);
		for (int i = 0; i < 10; i++) {
			tenths[i] = data[(int) ((data.length * (i + 1)) / 10 - 1)];
		}
		return tenths;
	}

}
