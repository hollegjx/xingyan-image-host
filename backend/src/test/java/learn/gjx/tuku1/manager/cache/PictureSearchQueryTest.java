package learn.gjx.tuku1.manager.cache;

import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.model.dto.picture.PictureQueryRequest;
import org.junit.jupiter.api.Test;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;

class PictureSearchQueryTest {
    @Test
    void dateFiltersAreCopiedAndRemainUnchangedWhenCallerMutatesItsDates() {
        PictureQueryRequest source = new PictureQueryRequest();
        source.setStartEditTime(new Date(1767225600000L));
        source.setEndEditTime(new Date(1767312000000L));
        source.setPicFormat(" PNG ");
        PictureQueryRequest query = PublicPictureQuery.normalizeFilters(source);
        source.getStartEditTime().setTime(0);
        source.getEndEditTime().setTime(0);
        assertEquals(1767225600000L, query.getStartEditTime().getTime());
        assertEquals(1767312000000L, query.getEndEditTime().getTime());
        assertEquals("png", query.getPicFormat());
        assertEquals(" PNG ", source.getPicFormat());
    }

    @Test
    void rejectsEqualOrReversedRangesAndUnsupportedDates() {
        PictureQueryRequest query = new PictureQueryRequest();
        query.setStartEditTime(new Date(10000));
        for (long end : new long[]{10000, 9999}) {
            query.setEndEditTime(new Date(end));
            assertThrows(BusinessException.class, () -> PublicPictureQuery.normalizeFilters(query));
        }
        query.setEndEditTime(null);
        assertNotNull(PublicPictureQuery.normalizeFilters(query).getStartEditTime());
        query.setStartEditTime(null);
        query.setEndEditTime(new Date(10000));
        assertNotNull(PublicPictureQuery.normalizeFilters(query).getEndEditTime());
        query.setStartEditTime(new Date(-28800001L));
        assertThrows(BusinessException.class, () -> PublicPictureQuery.normalizeFilters(query));
    }

    @Test
    void rejectsInvalidImageDimensionsSizeAndRatio() {
        for (int value : new int[]{0, -1}) {
            PictureQueryRequest query = new PictureQueryRequest();
            query.setPicWidth(value);
            assertThrows(BusinessException.class, () -> PublicPictureQuery.normalizeFilters(query));
            query.setPicWidth(null); query.setPicHeight(value);
            assertThrows(BusinessException.class, () -> PublicPictureQuery.normalizeFilters(query));
            query.setPicHeight(null); query.setPicSize((long) value);
            assertThrows(BusinessException.class, () -> PublicPictureQuery.normalizeFilters(query));
        }
        for (double value : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            PictureQueryRequest query = new PictureQueryRequest(); query.setPicScale(value);
            assertThrows(BusinessException.class, () -> PublicPictureQuery.normalizeFilters(query));
        }
    }
}
