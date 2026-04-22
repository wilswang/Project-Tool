package tool.whiteLabel;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@Data
public class FileConfig {

    private String name;

    @JsonProperty("isNew")
    private boolean isNew;

    private String location;
    private String template;

    private List<String> environments;

    private String marker;
    private boolean insertAfter = false;
    private List<String> imports;
}
