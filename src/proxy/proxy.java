package  proxy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import org.json.simple.parser.JSONParser;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import com.github.os72.protobuf.dynamic.DynamicSchema;
import com.github.os72.protobuf.dynamic.EnumDefinition;
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
	
	private static void getRequestParam(JSONObject endpointJson, DynamicSchema.Builder schemaBuilder, String filename, HashMap<String,MessageDefinition> messageDefinitionMap) throws DescriptorValidationException{
	
		String endpointName = (String) endpointJson.get("name");
		if (endpointJson.containsKey("parameter")){
			JSONObject parameter = (JSONObject)endpointJson.get("parameter");
			JSONObject fields = (JSONObject)parameter.get("fields");
			JSONArray paramList = (JSONArray)fields.get("Parameter");
			
			Builder builder = MessageDefinition.newBuilder(endpointName + "_request");
			
			MessageDefinition msgDef = getParams(builder, paramList);
			schemaBuilder.addMessageDefinition(msgDef);
			
			String key = filename + endpointName + "parameter" + "Parameter";
			messageDefinitionMap.put(key, msgDef);
		}
	}

	
	private static void getSuccessParam(JSONObject endpointJson, DynamicSchema.Builder schemaBuilder, String filename, HashMap<String,MessageDefinition> messageDefinitionMap) throws DescriptorValidationException{
		
		String endpointName = (String) endpointJson.get("name");
		if (endpointJson.containsKey("success")){
			JSONObject success = (JSONObject)endpointJson.get("success");
			JSONObject fields = (JSONObject)success.get("fields");
			JSONArray Success_200 = (JSONArray)fields.get("Success_200");
			
			Builder builder = MessageDefinition.newBuilder(endpointName + "_success");
			
			MessageDefinition msgDef = getParams(builder, Success_200);
			schemaBuilder.addMessageDefinition(msgDef);
			
			String key = filename + endpointName + "success" + "Success_200";
			messageDefinitionMap.put(key, msgDef);
		}
	}
	
	private static void getErrorParam(JSONObject endpointJson, DynamicSchema.Builder schemaBuilder, String filename, HashMap<String,MessageDefinition> messageDefinitionMap) throws DescriptorValidationException{
		
		String endpointName = (String) endpointJson.get("name");
		if (endpointJson.containsKey("error")){
			JSONObject success = (JSONObject)endpointJson.get("error");
			JSONObject fields = (JSONObject)success.get("fields");
			JSONArray Error_400 = (JSONArray)fields.get("Error_400");
			
			Builder builder = MessageDefinition.newBuilder(endpointName + "_error");
			
			MessageDefinition msgDef = getParams(builder, Error_400);
			schemaBuilder.addMessageDefinition(msgDef);
			
			String key = filename + endpointName + "error" + "Error_400";
			messageDefinitionMap.put(key, msgDef);
		}
	}
	
	private static MessageDefinition getParams(Builder builder, JSONArray paramArray){
		int i = 1;
		HashMap<String, Integer> processedParams = new HashMap<String, Integer>();
		
		for (Object p: paramArray) {
			JSONObject param = (JSONObject) p;
			String name = (String) param.get("field");
			String type = (String) param.get("type");
			
			if (!processedParams.containsKey(name)){
				if (param.containsKey("allowedValues")){
					// if param is of type enum
					EnumDefinition enumDef = getEnumDefinition(param);
					builder.addEnumDefinition(enumDef);		
					
				} else if (type.equals("object")){
					String grpcBuilderName = name;
					if(name.contains(".")){
						// If the parameter is child param, then remove parent name
						//grpcBuilderName = name.substring(name.lastIndexOf('.')+1);
						grpcBuilderName = name.replaceAll("\\.", "");
					}
					MessageDefinition.Builder msgBuilder = MessageDefinition.newBuilder(grpcBuilderName);
					MessageDefinition msgDefinition = getMessageDefinition(param, paramArray, msgBuilder, processedParams);
					builder.addMessageDefinition(msgDefinition);
					
				} else {
					// If param is scalar data type
					getScalarType(param, builder, i);
					i++;
				}
				processedParams.put(name, 1);
			}
		}
		
		MessageDefinition msgDef = builder.build();
		return msgDef;
	}
	
	private static EnumDefinition getEnumDefinition(JSONObject param) {
		
		String enumName = (String) param.get("field");
		JSONArray allowedValues = (JSONArray) param.get("allowedValues");
		
		EnumDefinition.Builder enumBuilder = EnumDefinition.newBuilder(enumName);
		
		int i=1;
		for (Object value: allowedValues){
			String stringValue = (String)value;
			stringValue = stringValue.replaceAll("\"", "");
			enumBuilder.addValue(stringValue, i);
			i++;
		}
		return enumBuilder.build();
	}

	private static MessageDefinition getMessageDefinition(JSONObject param, JSONArray paramArray, MessageDefinition.Builder msgBuilder, HashMap<String, Integer> processedParams){
		
		String currentParam = (String) param.get("field");
		JSONArray childParams = findAllChildParams(paramArray, currentParam);
		
		int i=1;
		for(Object childParam: childParams){
			JSONObject jsonChildParam = (JSONObject) childParam;
			String type = (String) jsonChildParam.get("type");
			String name = (String) jsonChildParam.get("field");
			
			if(!type.equals("object")){
				// If param is scalar data type
				if (!processedParams.containsKey(name)){
					
					getScalarType(jsonChildParam, msgBuilder, i);			
					processedParams.put(name, 1);
					i++;
				}
			}else{
				String grpcBuilderName = name;
				if(name.contains(".")){
					// If the parameter is child param, then remove parent name
					// grpcBuilderName = name.substring(name.lastIndexOf('.')+1);
					grpcBuilderName = name.replaceAll("\\.", "");
				}
				MessageDefinition.Builder childMsgBuilder = MessageDefinition.newBuilder(grpcBuilderName);
				MessageDefinition childMsgDefinition = getMessageDefinition(jsonChildParam, paramArray, childMsgBuilder, processedParams);
				msgBuilder.addMessageDefinition(childMsgDefinition);
			}
		}
		return msgBuilder.build();
	}
	
	private static void getScalarType(JSONObject param, MessageDefinition.Builder msgBuilder, int fieldIndex){
	
		String type = (String) param.get("type");
		String name = (String) param.get("field");
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
		
		String grpcFieldName = name;
		if(name.contains(".")){
			// If the parameter is child param, then remove parent name
			grpcFieldName = name.substring(name.lastIndexOf('.')+1);
		}
					
		msgBuilder.addField(label, type, grpcFieldName, fieldIndex);
	}
	
	private static JSONArray findAllChildParams(JSONArray paramArray, String parentName){
		
		JSONArray childParams = new JSONArray();
		
		for (Object param: paramArray) {
			
			JSONObject jsonParam = (JSONObject) param;
			String paramName = (String)jsonParam.get("field");
			//if(paramName.startsWith(parentName) && !paramName.equalsIgnoreCase(parentName) && ){
			if( paramName.matches(parentName + ".[^.]+") && !paramName.equalsIgnoreCase(parentName)){
				childParams.add(jsonParam);
			}
		}
		return childParams;
	}
	
	public static void main(String []args){
		
		getDynamicMsg("E://Kinto//proxy//proxy//src//proxy//fields.json");
	}
}