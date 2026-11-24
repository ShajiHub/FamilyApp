package com.shajitha.familyapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** The feature catalogue loads correctly, however it is first used. */
@RunWith(AndroidJUnit4.class)
public class FeaturesTest {

    @Test
    public void categoryFirstThenFeatures() {
        // Touching a Category first (as opening a hub does) must not leave features without a category.
        Features.Category daily = Features.Category.valueOf("DAILY_LIFE");
        assertFalse(Features.in(daily).isEmpty());
        for (Features.Feature f : Features.ALL) {
            assertNotNull(f.category);
        }
        assertEquals(6, Features.Category.values().length);
    }
}
