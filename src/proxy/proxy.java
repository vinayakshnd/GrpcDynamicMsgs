package  proxy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.json.*;
import org.json.simple.parser.JSONParser;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import com.github.os72.protobuf.dynamic.DynamicSchema;
import com.github.os72.protobuf.dynamic.MessageDefinition;
import com.github.os72.protobuf.dynamic.MessageDefinition.Builder;
import com.google.protobuf.Descriptors.DescriptorValidationException;

import org.json.simple.JSONArray;

class proxy{
	
	public static HashMap<String,MessageDefinition> getDynamicMsg(String jsonPath){		
		
		String content = "";
		String filename = "";
		HashMap<String,MessageDefinition> messageDefinitionMap = new HashMap<String,MessageDefinition>();  
		
		// Read json file content
		try{
			Path path = Paths.get(jsonPath);
			filename = path.getFileName().toString();
			content = new String(Files.readAllBytes(path));
		
		}catch(IOException e){
			System.out.printf("Failed to read %s file", jsonPath);
			System.out.println(e);
			return null;
		}
		
		DynamicSchema.Builder schemaBuilder = DynamicSchema.newBuilder();
		schemaBuilder.setName(filename + ".proto");
		
		JSONParser parser = new JSONParser();
		
		try {
			Object obj = parser.parse(content);
			JSONArray endpointsArray = (JSONArray) obj;
			
			// For each of the endpoint
			for (Object endpoint: endpointsArray){
				
				JSONObject  endpointJson = (JSONObject) endpoint;
				
				getRequestParam(endpointJson, schemaBuilder, filename, messageDefinitionMap);
				getSuccessParam(endpointJson, schemaBuilder, filename, messageDefinitionMap);
				getErrorParam(endpointJson, schemaBuilder, filename, messageDefinitionMap);	
			}	

    		DynamicSchema schema = schemaBuilder.build();
    		System.out.println(schema);
    		
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (DescriptorValidationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return messageDefinitionMap;	
	}
	
	public static void getRequestParam(JSONObject endpointJson, DynamicSchema.Builder schemaBuilder, String filename, HashMap<String,MessageDefinition> messageDefinitionMap) throws DescriptorValidationException{
	
		String endpointName = (String) endpointJson.get("name");
		JSONObject parameter = (JSONObject)endpointJson.get("parameter");
		JSONObject fields = (JSONObject)parameter.get("fields");
		JSONArray paramList = (JSONArray)fields.get("Parameter");
		
		Builder builder = MessageDefinition.newBuilder(endpointName + "_parameter");
		
		MessageDefinition msgDef = getParams(builder, paramList);
		schemaBuilder.addMessageDefinition(msgDef);
		
		String key = filename + endpointName + "parameter" + "Parameter";
		messageDefinitionMap.put(key, msgDef);
	}

	
	public static void getSuccessParam(JSONObject endpointJson, DynamicSchema.Builder schemaBuilder, String filename, HashMap<String,MessageDefinition> messageDefinitionMap) throws DescriptorValidationException{
		
		String endpointName = (String) endpointJson.get("name");
		JSONObject success = (JSONObject)endpointJson.get("success");
		JSONObject fields = (JSONObject)success.get("fields");
		JSONArray Success_200 = (JSONArray)fields.get("Success_200");
		
		Builder builder = MessageDefinition.newBuilder(endpointName + "_success");
		
		MessageDefinition msgDef = getParams(builder, Success_200);
		schemaBuilder.addMessageDefinition(msgDef);
		
		String key = filename + endpointName + "success" + "Success_200";
		messageDefinitionMap.put(key, msgDef);
	}
	
	public static void getErrorParam(JSONObject endpointJson, DynamicSchema.Builder schemaBuilder, String filename, HashMap<String,MessageDefinition> messageDefinitionMap) throws DescriptorValidationException{
		
		String endpointName = (String) endpointJson.get("name");
		JSONObject success = (JSONObject)endpointJson.get("error");
		JSONObject fields = (JSONObject)success.get("fields");
		JSONArray Error_400 = (JSONArray)fields.get("Error_400");
		
		Builder builder = MessageDefinition.newBuilder(endpointName + "_error");
		
		MessageDefinition msgDef = getParams(builder, Error_400);
		schemaBuilder.addMessageDefinition(msgDef);
		
		String key = filename + endpointName + "error" + "Error_400";
		messageDefinitionMap.put(key, msgDef);
	}
	
	public static MessageDefinition getParams(Builder builder, JSONArray paramArray){
		int i = 1;
		for (Object p : paramArray) {
			JSONObject param = (JSONObject) p;
			String name = (String) param.get("field");
			String type = (String) param.get("type");
			
			boolean optional = (Boolean) param.get("optional");
			boolean required = !optional;
			String label;
			if (required == true) {
				label = "required";
			}else{
				label = "optional";
			}
			
			// There seems to be only one way to specify 
			// if dataType is array (`repeated` in gRPC terms), ie. through label.
			// label is `DescriptorProtos.FieldDescriptorProto.Label` enum
			// and can take only one of the value :optional, required, repeated.
			if (type.contains("[]")){
				type = type.replace("[]", "");
				label = "repeated";
			}
						
			builder.addField(label, type, name, i);
			i++;
		}
		
		MessageDefinition msgDef = builder.build();
		return msgDef;
	}
	
	public static void main(String []args){
		
		getDynamicMsg("E://Kinto//proxy//proxy//src//proxy//fields.json");
	}
}