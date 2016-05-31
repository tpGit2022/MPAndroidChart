package com.github.mikephil.charting.highlight;

import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;
import com.github.mikephil.charting.interfaces.dataprovider.BarDataProvider;
import com.github.mikephil.charting.interfaces.datasets.IDataSet;
import com.github.mikephil.charting.utils.SelectionDetail;

/**
 * Created by Philipp Jahoda on 22/07/15.
 */
public class BarHighlighter extends ChartHighlighter<BarDataProvider> {

	public BarHighlighter(BarDataProvider chart) {
		super(chart);
	}

	@Override
	public Highlight getHighlight(float x, float y) {

		BarData barData = mChart.getBarData();

		final float xVal = getXForTouch(x);
		final float baseNoSpace = getBase(x);
		final int setCount = barData.getDataSetCount();
		int dataSetIndex = ((int)baseNoSpace) % setCount;

		if (dataSetIndex < 0) {
			dataSetIndex = 0;
		} else if (dataSetIndex >= setCount) {
			dataSetIndex = setCount - 1;
		}

		SelectionDetail selectionDetail = getSelectionDetail(xVal, x, y, dataSetIndex);
		if (selectionDetail == null)
			return null;

		IBarDataSet set = barData.getDataSetByIndex(dataSetIndex);
		if (set.isStacked()) {

			float[] pts = new float[2];
			pts[1] = y;

			// take any transformer to determine the xPx-axis yValue
			mChart.getTransformer(set.getAxisDependency()).pixelsToValue(pts);

			return getStackedHighlight(selectionDetail,
					set,
					xVal,
					pts[1]);
		}

		return new Highlight(
				xVal,
				selectionDetail.yValue,
				selectionDetail.dataIndex,
				selectionDetail.dataSetIndex,
				-1);
	}

	@Override
	protected float getXForTouch(float x) {

		if (!mChart.getBarData().isGrouped()) {
			return super.getXForTouch(x);
		} else {

			float baseNoSpace = getBase(x);

			int setCount = mChart.getBarData().getDataSetCount();
			int xIndex = (int) baseNoSpace / setCount;

			int valCount = mChart.getData().getXValCount();

			if (xIndex < 0)
				xIndex = 0;
			else if (xIndex >= valCount)
				xIndex = valCount - 1;

			return xIndex;
		}
	}

	@Override
	protected SelectionDetail getSelectionDetail(float xVal, float x, float y, int dataSetIndex) {

		dataSetIndex = Math.max(dataSetIndex, 0);

		BarData barData = mChart.getBarData();
		IDataSet dataSet = barData.getDataSetCount() > dataSetIndex
				? barData.getDataSetByIndex(dataSetIndex)
				: null;
		if (dataSet == null)
			return null;

		final float yValue = dataSet.getYValueForXValue(xVal);

		if (yValue == Double.NaN) return null;

		return new SelectionDetail(0f,
				yValue,
				dataSetIndex,
				dataSet);
	}

	/**
	 * This method creates the Highlight object that also indicates which yValue of a stacked BarEntry has been selected.
	 *
	 * @param selectionDetail the selection detail to work with looking for stacked values
	 * @param set
	 * @param xVal
	 * @param yValue
	 * @return
	 */
	protected Highlight getStackedHighlight(
			SelectionDetail selectionDetail,
			IBarDataSet set,
			float xVal,
			double yValue) {

		BarEntry entry = set.getEntryForXPos(xVal);

		if (entry == null)
			return null;

		if (entry.getYVals() == null) {
			return new Highlight(xVal,
					entry.getY(),
					selectionDetail.dataIndex,
					selectionDetail.dataSetIndex);
		}

		Range[] ranges = getRanges(entry);
		if (ranges.length > 0) {
			int stackIndex = getClosestStackIndex(ranges, (float)yValue);
			return new Highlight(
					xVal,
					entry.getPositiveSum() - entry.getNegativeSum(),
					selectionDetail.dataIndex,
					selectionDetail.dataSetIndex,
					stackIndex,
					ranges[stackIndex]
			);
		}

		return null;
	}

	/**
	 * Returns the index of the closest yValue inside the values array / ranges (stacked barchart) to the yValue given as
	 * a parameter.
	 *
	 * @param ranges
	 * @param value
	 * @return
	 */
	protected int getClosestStackIndex(Range[] ranges, float value) {

		if (ranges == null || ranges.length == 0)
			return 0;

		int stackIndex = 0;

		for (Range range : ranges) {
			if (range.contains(value))
				return stackIndex;
			else
				stackIndex++;
		}

		int length = Math.max(ranges.length - 1, 0);

		return (value > ranges[length].to) ? length : 0;
		//
		// float[] vals = e.getYVals();
		//
		// if (vals == null)
		// return -1;
		//
		// int index = 0;
		// float remainder = e.getNegativeSum();
		//
		// while (index < vals.length - 1 && yValue > vals[index] + remainder) {
		// remainder += vals[index];
		// index++;
		// }
		//
		// return index;
	}

	/**
	 * Returns the base xPx-yValue to the corresponding xPx-touch yValue in pixels.
	 * 
	 * @param x
	 * @return
	 */
	protected float getBase(float x) {

		// create an array of the touch-point
		float[] pts = new float[2];
		pts[0] = x;

		// take any transformer to determine the xPx-axis yValue
		mChart.getTransformer(YAxis.AxisDependency.LEFT).pixelsToValue(pts);
		float xVal = pts[0];

		int setCount = mChart.getBarData().getDataSetCount();

		// calculate how often the group-space appears
		int steps = (int) ((float) xVal / ((float) setCount + mChart.getBarData().getGroupSpace()));

		float groupSpaceSum = mChart.getBarData().getGroupSpace() * (float) steps;

		float baseNoSpace = (float) xVal - groupSpaceSum;
		return baseNoSpace;
	}

	/**
	 * Splits up the stack-values of the given bar-entry into Range objects.
	 * 
	 * @param entry
	 * @return
	 */
	protected Range[] getRanges(BarEntry entry) {

		float[] values = entry.getYVals();

		if (values == null || values.length == 0)
			return new Range[0];

		Range[] ranges = new Range[values.length];

		float negRemain = -entry.getNegativeSum();
		float posRemain = 0f;

		for (int i = 0; i < ranges.length; i++) {

			float value = values[i];

			if (value < 0) {
				ranges[i] = new Range(negRemain, negRemain + Math.abs(value));
				negRemain += Math.abs(value);
			} else {
				ranges[i] = new Range(posRemain, posRemain + value);
				posRemain += value;
			}
		}

		return ranges;
	}
}
