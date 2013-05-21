/**
 */
package org.ietr.preesm.experiment.model.pimm;

import org.eclipse.emf.ecore.EObject;


/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Expression</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * <ul>
 *   <li>{@link org.ietr.preesm.experiment.model.pimm.Expression#getExpressionString <em>Expression String</em>}</li>
 *   <li>{@link org.ietr.preesm.experiment.model.pimm.Expression#getValueString <em>Value String</em>}</li>
 * </ul>
 * </p>
 *
 * @see org.ietr.preesm.experiment.model.pimm.PiMMPackage#getExpression()
 * @model
 * @generated
 */
public interface Expression extends EObject {

	/**
	 * Returns the value of the '<em><b>Expression String</b></em>' attribute.
	 * The default value is <code>"0"</code>.
	 * <!-- begin-user-doc -->
	 * <p>
	 * If the meaning of the '<em>Expression String</em>' attribute isn't clear,
	 * there really should be more of a description here...
	 * </p>
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Expression String</em>' attribute.
	 * @see #setExpressionString(String)
	 * @see org.ietr.preesm.experiment.model.pimm.PiMMPackage#getExpression_ExpressionString()
	 * @model default="0"
	 * @generated
	 */
	String getExpressionString();

	/**
	 * Sets the value of the '{@link org.ietr.preesm.experiment.model.pimm.Expression#getExpressionString <em>Expression String</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Expression String</em>' attribute.
	 * @see #getExpressionString()
	 * @generated
	 */
	void setExpressionString(String value);

	/**
	 * Returns the value of the '<em><b>Value String</b></em>' attribute.
	 * The default value is <code>"0"</code>.
	 * <!-- begin-user-doc -->
	 * <p>
	 * If the meaning of the '<em>Value String</em>' attribute isn't clear,
	 * there really should be more of a description here...
	 * </p>
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Value String</em>' attribute.
	 * @see #setValueString(String)
	 * @see org.ietr.preesm.experiment.model.pimm.PiMMPackage#getExpression_ValueString()
	 * @model default="0"
	 * @generated
	 */
	String getValueString();

	/**
	 * Sets the value of the '{@link org.ietr.preesm.experiment.model.pimm.Expression#getValueString <em>Value String</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Value String</em>' attribute.
	 * @see #getValueString()
	 * @generated
	 */
	void setValueString(String value);

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @model
	 * @generated
	 */
	int evaluate(String str);

	
} // Expression