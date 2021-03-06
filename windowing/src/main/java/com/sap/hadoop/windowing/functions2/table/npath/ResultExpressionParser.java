package com.sap.hadoop.windowing.functions2.table.npath;

import java.util.ArrayList;
import java.util.Iterator;

import org.apache.hadoop.hive.ql.exec.ExprNodeEvaluator;
import org.apache.hadoop.hive.ql.exec.ExprNodeEvaluatorFactory;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.parse.ASTNode;
import org.apache.hadoop.hive.ql.parse.RowResolver;
import org.apache.hadoop.hive.ql.parse.TypeCheckCtx;
import org.apache.hadoop.hive.ql.plan.ExprNodeColumnDesc;
import org.apache.hadoop.hive.ql.plan.ExprNodeDesc;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.StructObjectInspector;

import com.sap.hadoop.HiveUtils;
import com.sap.hadoop.windowing.WindowingException;
import com.sap.hadoop.windowing.parser.ParseUtils;
import com.sap.hadoop.windowing.query2.specification.SelectSpec;
import com.sap.hadoop.windowing.query2.translate.TranslateUtils;

/*
 * ResultExpression is a Select List with the following variation:
 * - the select keyword is optional. The parser checks if the expression doesn't start with select; if not it prefixes it.
 * - Window Fn clauses are not permitted.
 * - expressions can operate on the input columns plus the psuedo column 'path' which is array of structs. The shape of the struct is
 *   the same as the input.
 * 
 */
public class ResultExpressionParser
{
	String resultExprString;
	
	StructObjectInspector selectListInputOI;
	RowResolver selectListInputRowResolver;
	TypeCheckCtx selectListInputTypeCheckCtx;
	
	SelectSpec selectSpec;

	ArrayList<ExprNodeEvaluator> selectListExprEvaluators;
	ArrayList<ObjectInspector> selectListExprOIs;
	ObjectInspector selectListOutputOI;
	
	public ResultExpressionParser(String resultExprString, StructObjectInspector selectListInputOI)
	{
		this.resultExprString = resultExprString;
		this.selectListInputOI = selectListInputOI;
	}
	
	public void translate() throws WindowingException
	{
		setupSelectListInputInfo();
		fixResultExprString();
		parse();
		validateSelectExpr();
		buildSelectListEvaluators();
	}
	
	public ArrayList<ExprNodeEvaluator> getSelectListExprEvaluators()
	{
		return selectListExprEvaluators;
	}

	public StructObjectInspector getSelectListOutputOI()
	{
		return (StructObjectInspector) selectListOutputOI;
	}

	private void buildSelectListEvaluators() throws WindowingException
	{
		selectListExprEvaluators = new ArrayList<ExprNodeEvaluator>();
		selectListExprOIs = new ArrayList<ObjectInspector>();
		ArrayList<String> selectListExprNames = new ArrayList<String>();
		int i = 0;
		Iterator<Object> it = selectSpec.getColumnListAndAlias();
		while(it.hasNext())
		{
			Object[] selectColDetails = (Object[]) it.next();
			String selectColName = (String) selectColDetails[1];
			ASTNode selectColumnNode = (ASTNode) selectColDetails[2];
			ExprNodeDesc selectColumnExprNode = TranslateUtils.buildExprNode(selectColumnNode, selectListInputTypeCheckCtx);
			ExprNodeEvaluator selectColumnExprEval = ExprNodeEvaluatorFactory.get(selectColumnExprNode);
			ObjectInspector selectColumnOI = null;
			try
			{
				selectColumnOI = selectColumnExprEval.initialize(selectListInputOI);
			}
			catch (HiveException he)
			{
				throw new WindowingException(he);
			}
			
			selectColName = getColumnName(selectColName, selectColumnExprNode, i);
			
			selectListExprEvaluators.add(selectColumnExprEval);
			selectListExprOIs.add(selectColumnOI);
			selectListExprNames.add(selectColName);
			i++;
		}
		
		selectListOutputOI = ObjectInspectorFactory.getStandardStructObjectInspector(selectListExprNames, selectListExprOIs);
	}
	
	private void setupSelectListInputInfo() throws WindowingException
	{
		selectListInputRowResolver = HiveUtils.getRowResolver("npathInput", selectListInputOI);
		selectListInputTypeCheckCtx = new TypeCheckCtx(selectListInputRowResolver);
		selectListInputTypeCheckCtx.setUnparseTranslator(null);
	}
	
	private void fixResultExprString()
	{
		String r = resultExprString.trim();
		String prefix = r.substring(0, 6);
		if ( !prefix.toLowerCase().equals("select"))
		{
			r = "select " + r;
		}
		resultExprString = r;
	}
	
	private void parse() throws WindowingException
	{
		selectSpec = ParseUtils.parseSelect(resultExprString);
	}
	
	private void validateSelectExpr() throws WindowingException
	{
		if (selectSpec.getWindowFuncs() != null )
		{
			throw new WindowingException("NPath Result Expression cannot have Windowing Function expressions");
		}
		
		for(ASTNode node : selectSpec.getExpressions())
		{
			TranslateUtils.validateNoLeadLagInValueBoundarySpec(node, "Lead/Lag not allowed in NPath Result Expression");
		}
	}
	
	private String getColumnName(String alias, ExprNodeDesc exprNode, int colIdx)
	{
		if ( alias != null )
		{
			return alias;
		}
		else if ( exprNode instanceof ExprNodeColumnDesc )
		{
			ExprNodeColumnDesc colDesc = (ExprNodeColumnDesc) exprNode;
			return colDesc.getColumn();
		}
		return "npath_col_" + colIdx;
	}
}
