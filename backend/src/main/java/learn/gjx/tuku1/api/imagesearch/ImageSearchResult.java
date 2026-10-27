package learn.gjx.tuku1.api.imagesearch;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageSearchResult {
    private String thumbUrl;
    private String fromUrl;
}
