package vn.vkey.minh_nguyen.test

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import vn.vkey.minh_nguyen.rulervaluepicker.RulerValuePicker
import vn.vkey.minh_nguyen.test.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupPickers()
    }

    private fun setupPickers() {
        // Horizontal Bottom
        binding.pickerHorizontalBottom.onValueChangedListener =
            object : RulerValuePicker.OnValueChangedListener {
                override fun onValueChanged(picker: RulerValuePicker, value: Float) {
                    binding.tvValueHorizontalBottom.text = "Value: ${formatValue(value)}"
                }
            }

        // Horizontal Top
        binding.pickerHorizontalTop.onValueChangedListener =
            object : RulerValuePicker.OnValueChangedListener {
                override fun onValueChanged(picker: RulerValuePicker, value: Float) {
                    binding.tvValueHorizontalTop.text = "Value: ${formatValue(value)}"
                }
            }

        // Vertical Left
        binding.pickerVerticalLeft.onValueChangedListener =
            object : RulerValuePicker.OnValueChangedListener {
                override fun onValueChanged(picker: RulerValuePicker, value: Float) {
                    binding.tvValueVerticalLeft.text = "Value: ${formatValue(value)}"
                }
            }

        // Vertical Right
        binding.pickerVerticalRight.onValueChangedListener =
            object : RulerValuePicker.OnValueChangedListener {
                override fun onValueChanged(picker: RulerValuePicker, value: Float) {
                    binding.tvValueVerticalRight.text = "Value: ${formatValue(value)}"
                }
            }
    }

    private fun formatValue(value: Float): String {
        return if (value == value.toLong().toFloat()) {
            value.toLong().toString()
        } else {
            String.format("%.1f", value)
        }
    }
}