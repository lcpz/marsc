package toolkit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.RepeatedTest;

import com.javadocmd.simplelatlng.LatLngTool;
import com.javadocmd.simplelatlng.util.LengthUnit;

import locations.LocationLatLng;

class LocationLatLngTest {

	/**
	 * Ensuring that SimpleLatLng calculations do not vary because of approximation.
	 */
	@RepeatedTest(10)
	void test() {
		LocationLatLng l1 = new LocationLatLng(51.541731916800003, 0.084550257399999995);
		LocationLatLng l2 = new LocationLatLng(51.542604857100002, -0.35444340219999998);

		int t1 = l1.getTravelTimeTo(l2, 1);
		int t2 = l1.getTravelTimeTo(l2, 1);

		System.out.println(LatLngTool.distance(l1.location, l2.location, LengthUnit.KILOMETER) + " " + t1);

		assertEquals(t1, t2);
	}

}